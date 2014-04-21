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

import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.genomics.Genomics;
import com.google.api.services.genomics.model.Call;
import com.google.api.services.genomics.model.SearchVariantsRequest;
import com.google.api.services.genomics.model.SearchVariantsResponse;
import com.google.api.services.genomics.model.Variant;
import com.google.appengine.tools.mapreduce.*;
import com.google.appengine.tools.mapreduce.outputs.GoogleCloudStorageFileOutput;
import com.google.appengine.tools.mapreduce.outputs.MarshallingOutput;
import com.google.common.collect.Lists;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

public class MainServlet extends HttpServlet {

  public static final String BUCKET_NAME = "cloud-genomics-mapreduce-tests";
  public static final String OUTPUT_FILE_NAME = "VariantSimilarity.txt";
  public static final String API_KEY = "AIzaSyCepkTbGZbHcUwLu5VXjxtOGoGpv1o8hhA";
  public static final int SHARDS = 100;

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String datasetId = req.getParameter("datasetId");
    String contig = req.getParameter("contig");
    Integer start = Integer.valueOf(req.getParameter("start"));
    Integer end = Integer.valueOf(req.getParameter("end"));

    Integer shards = end - start < 1000 ? 1 : SHARDS;

    Output<String, GoogleCloudStorageFileSet> output = new MarshallingOutput<String, GoogleCloudStorageFileSet>(
        new GoogleCloudStorageFileOutput(BUCKET_NAME, OUTPUT_FILE_NAME, "text/plain",
            1 /* we only want one results file */),
        Marshallers.getStringMarshaller());

    MapReduceSpecification spec = MapReduceSpecification.of("VariantSimilarityMapreduce",
        new GenomicsApiInput(datasetId, contig, start, end, shards),
        new VariantSimilarityMapper(),
        Marshallers.getStringMarshaller(),
        Marshallers.getIntegerMarshaller(),
        new SummingReducer(),
        output);

    String jobId = MapReduceJob.start(spec, new MapReduceSettings().setBucketName(BUCKET_NAME));

    resp.sendRedirect("/_ah/pipeline/status.html?root=" + jobId);
  }

  private static class VariantSimilarityInput {
    public final Integer sequenceStart;
    public final Integer sequenceEnd;
    public final List<Variant> variants;

    public VariantSimilarityInput(Integer sequenceStart, Integer sequenceEnd, List<Variant> variants) {
      this.sequenceStart = sequenceStart;
      this.sequenceEnd = sequenceEnd;
      this.variants = variants;
    }
  }

  private static class GenomicsApiInput extends Input<VariantSimilarityInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInput.class.getName());

    private final String datasetId;
    private final String contig;
    private final int start;
    private final int end;
    private final int shards;

    public GenomicsApiInput(String datasetId, String contig, int start, int end, int shards) {
      this.datasetId = datasetId;
      this.contig = contig;
      this.start = start;
      this.end = end;
      this.shards = shards;
    }

    @Override
    public List<GenomicsApiInputReader> createReaders() throws IOException {
      int rangeLength = (end - start) / shards;

      List<GenomicsApiInputReader> readers = Lists.newArrayList();
      for (int i = 0; i < shards; i++) {
        int rangeStart = start + (rangeLength * i);
        int rangeEnd = Math.max(end, rangeStart + rangeLength);
        readers.add(new GenomicsApiInputReader(datasetId, contig, rangeStart, rangeEnd));
        LOG.info("Adding reader " + rangeStart + ":" + rangeEnd);
      }

      return readers;
    }
  }

  private static class GenomicsApiInputReader extends InputReader<VariantSimilarityInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInputReader.class.getName());

    private final String datasetId;
    private final String contig;
    private final int start;
    private final int end;

    private boolean firstTime = true;
    private String nextPageToken;

    public GenomicsApiInputReader(String datasetId, String contig, int start, int end) {
      this.datasetId = datasetId;
      this.contig = contig;
      this.start = start;
      this.end = end;
    }

    private static Genomics getService() {
      // TODO: This auth doesn't work when running locally
      final AppIdentityCredential credential =
          new AppIdentityCredential(Lists.newArrayList("https://www.googleapis.com/auth/genomics"));

      return new Genomics.Builder(new UrlFetchTransport(), new JacksonFactory(), credential)
          .setRootUrl("https://www.googleapis.com/")
          .setApplicationName("mapreduce-java")
          .build();
    }

    @Override
    public VariantSimilarityInput next() throws IOException, NoSuchElementException {
      if (!firstTime && nextPageToken == null) {
        throw new NoSuchElementException();
      }
      firstTime = false;

      SearchVariantsRequest request = new SearchVariantsRequest()
          .setDatasetId(datasetId)
          .setContig(contig)
          .setStartPosition((long) start)
          .setEndPosition((long) end);

      if (nextPageToken != null) {
        request.setPageToken(nextPageToken);
      }

      try {
        SearchVariantsResponse response = getService().variants().search(request).setKey(API_KEY).execute();
        nextPageToken = response.getNextPageToken();
        LOG.info("Got " + response.getVariants().size() + " variants");
        return new VariantSimilarityInput(start, end, response.getVariants());

      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private static class VariantSimilarityMapper extends Mapper<VariantSimilarityInput, String, Integer> {
    private static final Logger LOG = Logger.getLogger(VariantSimilarityMapper.class.getName());

    @Override
    public void map(VariantSimilarityInput input) {
      for (Variant variant : input.variants) {
        List<String> samplesWithVariant = Lists.newArrayList();
        for (Call call : variant.getCalls()) {
          String genotype = call.getInfo().get("GT").get(0); // TODO: Change to use real genotype field
          genotype = genotype.replaceAll("[\\\\|0]", "");
          if (!genotype.isEmpty()) {
            samplesWithVariant.add(call.getCallsetId()); // TODO: Change to callsetName once available
          }
        }
        LOG.info("Variant " + variant.getId() + " has " + samplesWithVariant.size() + " actual calls");

        for (String s1 : samplesWithVariant) {
          for (String s2 : samplesWithVariant) {
            // TODO: Output can be reduced by half if we only output when s1 < s2
            emit(s1 + "-" + s2, 1);
          }
        }
      }
    }
  }

  private static class SummingReducer extends Reducer<String, Integer, String> {
    @Override
    public void reduce(String key, ReducerInput<Integer> values) {
      Integer sum = 0;
      while (values.hasNext()) {
        sum += values.next();
      }

      String output = key + "-" + sum + ":";
      emit(output);
    }
  }
}