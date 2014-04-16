/*
Copyright 2014 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.google.cloud.genomics.mapreduce;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.genomics.Genomics;
import com.google.api.services.genomics.model.Read;
import com.google.api.services.genomics.model.SearchReadsRequest;
import com.google.api.services.genomics.model.SearchReadsResponse;
import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.appengine.tools.mapreduce.GoogleCloudStorageFileSet;
import com.google.appengine.tools.mapreduce.MapReduceJob;
import com.google.appengine.tools.mapreduce.MapReduceSettings;
import com.google.appengine.tools.mapreduce.MapReduceSpecification;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Marshaller;
import com.google.appengine.tools.mapreduce.Marshallers;
import com.google.appengine.tools.mapreduce.Output;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.appengine.tools.mapreduce.inputs.ConsecutiveLongInput;
import com.google.appengine.tools.mapreduce.outputs.GoogleCloudStorageFileOutput;
import com.google.appengine.tools.mapreduce.outputs.MarshallingOutput;
import com.google.appengine.tools.mapreduce.outputs.InMemoryOutput;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.logging.Logger;

import java.io.IOException;
import javax.servlet.http.*;

public class MainServlet extends HttpServlet {

  public static final String BUCKET_NAME = "mybucket";
  public static final String OUTPUT_FILE_NAME = "ReadCoverage-%04d.txt";
  public static final String API_KEY = "myapikey";
  public static final int SHARDS = 1;

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String readsetId = req.getParameter("readsetId");
    String sequenceName = req.getParameter("sequenceName");
    String sequenceStart = req.getParameter("sequenceStart");
    String sequenceEnd = req.getParameter("sequenceEnd");

    Integer start = Integer.valueOf(sequenceStart);
    Integer end = Integer.valueOf(sequenceEnd);

    Output<String, GoogleCloudStorageFileSet> output = new MarshallingOutput(
        new GoogleCloudStorageFileOutput(BUCKET_NAME, OUTPUT_FILE_NAME, "text/plain", SHARDS),
        Marshallers.getStringMarshaller());

    MapReduceSpecification spec = MapReduceSpecification.of("ReadCoverageMapreduce",
        new GenomicsApiInput(readsetId, sequenceName, start, end, SHARDS),
        new ReadCoverageMapper(),
        Marshallers.getIntegerMarshaller(),
        Marshallers.getIntegerMarshaller(),
        new SummingReducer(),
        output);

    String jobId = MapReduceJob.start(spec, new MapReduceSettings().setBucketName(BUCKET_NAME));

    resp.sendRedirect("/_ah/pipeline/status.html?root=" + jobId);
  }

  private static class ReadsCoverageInput {
    public final Integer sequenceStart;
    public final Integer sequenceEnd;
    public final List<Read> reads;

    public ReadsCoverageInput(Integer sequenceStart, Integer sequenceEnd, List<Read> reads) {
      this.sequenceStart = sequenceStart;
      this.sequenceEnd = sequenceEnd;
      this.reads = reads;
    }
  }

  private static class GenomicsApiInput extends Input<ReadsCoverageInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInput.class.getName());

    private final String readsetId;
    private final String sequenceName;
    private final int start;
    private final int end;
    private final int shards;

    public GenomicsApiInput(String readsetId, String sequenceName, int start, int end, int shards) {
      this.readsetId = readsetId;
      this.sequenceName = sequenceName;
      this.start = start;
      this.end = end;
      this.shards = end - start < 100 ? 1 : shards;
    }

    @Override
    public List<GenomicsApiInputReader> createReaders() throws IOException {
      int rangeLength = (end - start) / shards;

      List<GenomicsApiInputReader> readers = Lists.newArrayList();
      for (int i = 0; i < shards; i++) {
        int rangeStart = start + (rangeLength * i);
        int rangeEnd = Math.max(end, rangeStart + rangeLength);
        readers.add(new GenomicsApiInputReader(readsetId, sequenceName, rangeStart, rangeEnd));
        LOG.info("Adding reader " + rangeStart + ":" + rangeEnd);
      }

      return readers;
    }
  }

  private static class GenomicsApiInputReader extends InputReader<ReadsCoverageInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInputReader.class.getName());

    private final String readsetId;
    private final String sequenceName;
    private final int start;
    private final int end;

    private boolean firstTime = true;
    private String nextPageToken;

    public GenomicsApiInputReader(String readsetId, String sequenceName, int start, int end) {
      this.readsetId = readsetId;
      this.sequenceName = sequenceName;
      this.start = start;
      this.end = end;
    }

    private static Genomics getService() {
      final AppIdentityCredential credential =
          new AppIdentityCredential(Lists.newArrayList("https://www.googleapis.com/auth/genomics"));

      return new Genomics.Builder(new UrlFetchTransport(), new JacksonFactory(), credential)
          .setRootUrl("https://www.googleapis.com/")
          .setApplicationName("mapreduce-java")
          .build();
    }

    @Override
    public ReadsCoverageInput next() throws IOException, NoSuchElementException {
      if (!firstTime && nextPageToken == null) {
        throw new NoSuchElementException();
      }
      firstTime = false;

      SearchReadsRequest request = new SearchReadsRequest()
          .setReadsetIds(Lists.newArrayList(readsetId))
          .setSequenceName(sequenceName)
          .setSequenceStart(BigInteger.valueOf(start))
          .setSequenceEnd(BigInteger.valueOf(end));

      if (nextPageToken != null) {
        request.setPageToken(nextPageToken);
      }

      try {
        SearchReadsResponse response = getService().reads().search(request).setKey(API_KEY).execute();
        nextPageToken = response.getNextPageToken();
        LOG.info("Got " + response.getReads().size() + " reads");
        return new ReadsCoverageInput(start, end, response.getReads());

      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private static class ReadCoverageMapper extends Mapper<ReadsCoverageInput, Integer, Integer> {
    private static final Logger LOG = Logger.getLogger(ReadCoverageMapper.class.getName());

    @Override
    public void map(ReadsCoverageInput input) {
      Map<Integer, Integer> coverage = new HashMap<Integer, Integer>();

      for (Read read : input.reads) {
        Integer readStart = read.getPosition();
        Integer readEnd = readStart + read.getOriginalBases().length();

        for (int i = Math.max(readStart, input.sequenceStart); i < Math.min(readEnd, input.sequenceEnd); i++) {
          Integer count = coverage.get(i);
          coverage.put(i, count == null ? 1 : count + 1);
        }
      }

      for (Map.Entry<Integer, Integer> entry : coverage.entrySet()) {
        LOG.info("Coverage for " + entry.getKey() + " - " + entry.getValue());
        emit(entry.getKey(), entry.getValue());
      }
    }
  }

  private static class SummingReducer extends Reducer<Integer, Integer, String> {
    private static final Logger LOG = Logger.getLogger(SummingReducer.class.getName());

    @Override
    public void reduce(Integer key, ReducerInput<Integer> values) {
      Integer coverage = 0;
      while (values.hasNext()) {
        coverage += values.next();
      }

      String output = key + ": " + coverage + "\n";
      LOG.info("Emitting " + output);
      emit(output);
    }
  }
}