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
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Maps;
import com.google.api.services.genomics.Genomics;
import com.google.api.services.genomics.model.Call;
import com.google.api.services.genomics.model.SearchVariantsRequest;
import com.google.api.services.genomics.model.SearchVariantsResponse;
import com.google.api.services.genomics.model.Variant;
import com.google.appengine.tools.mapreduce.GoogleCloudStorageFileSet;
import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.appengine.tools.mapreduce.MapReduceJob;
import com.google.appengine.tools.mapreduce.MapReduceSettings;
import com.google.appengine.tools.mapreduce.MapReduceSpecification;
import com.google.appengine.tools.mapreduce.Mapper;
import com.google.appengine.tools.mapreduce.Marshallers;
import com.google.appengine.tools.mapreduce.Output;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.google.appengine.tools.mapreduce.outputs.GoogleCloudStorageFileOutput;
import com.google.appengine.tools.mapreduce.outputs.MarshallingOutput;
import com.google.common.collect.Lists;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

public class MainServlet extends HttpServlet {

  public static final String API_KEY_PROPERTY = "genomics-mapreduce.api-key";
  public static final String BUCKET_NAME_PROPERTY = "genomics-mapreduce.bucket-name";
  public static final String OUTPUT_FILE_NAME_PROPERTY = "genomics-mapreduce.output-file-name";
  public static final String SHARDS_PROPERTY = "genomics-mapreduce.shards";
  public static final String QUEUE_NAME = "genomics-mapreduce-queue";

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String variantSetId = req.getParameter("variantSetId");
    String referenceName = req.getParameter("referenceName");
    Integer start = Integer.valueOf(req.getParameter("start"));
    Integer end = Integer.valueOf(req.getParameter("end"));

    Integer shards = end - start < 1000 ? 1 : Integer.valueOf(System.getProperty(SHARDS_PROPERTY));
    String bucketName = System.getProperty(BUCKET_NAME_PROPERTY);
    String outputFileName = System.getProperty(OUTPUT_FILE_NAME_PROPERTY);
    String apiKey = System.getProperty(API_KEY_PROPERTY);

    Output<String, GoogleCloudStorageFileSet> output = new MarshallingOutput<String, GoogleCloudStorageFileSet>(
        new GoogleCloudStorageFileOutput(bucketName, outputFileName, "text/plain"),
        Marshallers.getStringMarshaller());

    MapReduceSpecification.Builder<VariantSimilarityInput, String, Integer, String,
        GoogleCloudStorageFileSet> builder = new MapReduceSpecification.Builder<
        VariantSimilarityInput, String, Integer, String, GoogleCloudStorageFileSet>()
        .setJobName("VariantSimilarityMapreduce")
        .setInput(new GenomicsApiInput(apiKey, variantSetId, referenceName, start, end, shards))
        .setMapper(new VariantSimilarityMapper())
        .setKeyMarshaller(Marshallers.getStringMarshaller())
        .setValueMarshaller(Marshallers.getIntegerMarshaller())
        .setReducer(new SummingReducer())
        .setOutput(output);

    String jobId = MapReduceJob.start(builder.build(), new MapReduceSettings.Builder()
        .setWorkerQueueName(QUEUE_NAME).setBucketName(bucketName).build());

    resp.sendRedirect("/_ah/pipeline/status.html?root=" + jobId);
  }

  private static class VariantSimilarityInput {
    public final Integer sequenceStart;
    public final Integer sequenceEnd;
    public final List<Variant> variants;

    public VariantSimilarityInput(Integer sequenceStart, Integer sequenceEnd,
        List<Variant> variants) {
      this.sequenceStart = sequenceStart;
      this.sequenceEnd = sequenceEnd;
      this.variants = variants;
    }
  }

  private static class GenomicsApiInput extends Input<VariantSimilarityInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInput.class.getName());

    private final String apiKey;
    private final String variantSetId;
    private final String referenceName;
    private final int start;
    private final int end;
    private final int shards;

    public GenomicsApiInput(String apiKey, String variantSetId, String referenceName,
        int start, int end, int shards) {
      this.apiKey = apiKey;
      this.variantSetId = variantSetId;
      this.referenceName = referenceName;
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
        int rangeEnd = Math.min(end, rangeStart + rangeLength);
        readers.add(new GenomicsApiInputReader(apiKey, variantSetId, referenceName,
            rangeStart, rangeEnd));
        LOG.info("Adding reader " + rangeStart + ":" + rangeEnd);
      }

      return readers;
    }
  }

  private static class GenomicsApiInputReader extends InputReader<VariantSimilarityInput> {
    private static final Logger LOG = Logger.getLogger(GenomicsApiInputReader.class.getName());

    private final String apiKey;
    private final String variantSetId;
    private final String referenceName;
    private final int start;
    private final int end;

    private boolean firstTime = true;
    private String nextPageToken;

    public GenomicsApiInputReader(String apiKey, String variantSetId, String referenceName,
        int start, int end) {
      this.variantSetId = variantSetId;
      this.referenceName = referenceName;
      this.start = start;
      this.end = end;
      this.apiKey = apiKey;
    }

    private static Genomics getService() {
      return new Genomics.Builder(new UrlFetchTransport(), new JacksonFactory(), null)
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
          .setVariantSetIds(Lists.newArrayList(variantSetId))
          .setReferenceName(referenceName)
          .setStart((long) start)
          .setEnd((long) end);

      if (nextPageToken != null) {
        request.setPageToken(nextPageToken);
      }

      try {
        // We request the smallest partial response that we can in order to reduce the amount of data
        // we are fetching.
        SearchVariantsResponse response = getService().variants().search(request)
            .setFields("nextPageToken,variants(id,calls(info,callsetName))")
            .setKey(apiKey).execute();
        List<Variant> variants = response.getVariants();
        if (variants == null) {
          variants = Lists.newArrayList();
        }

        nextPageToken = response.getNextPageToken();
        LOG.info("Got " + variants.size() + " variants");
        return new VariantSimilarityInput(start, end, variants);

      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private static class VariantSimilarityMapper extends Mapper<VariantSimilarityInput, String, Integer> {
    private static final Logger LOG = Logger.getLogger(VariantSimilarityMapper.class.getName());

    private Map<String, Integer> counts;

    @Override
    public void beginSlice() {
      // We only emit counts once per slice in order to reduce the amount of rows we are writing overall
      // We use begin/endSlice because this map is too large to serialize (App Engine has a hard limit of 1MB)
      // in some cases, and so we can't use beginShard/endShard.
      counts = Maps.newHashMap();
    }

    @Override
    public void map(VariantSimilarityInput input) {
      for (Variant variant : input.variants) {
        List<String> samplesWithVariant = Lists.newArrayList();
        for (Call call : variant.getCalls()) {
          String genotype = call.getInfo().get("GT").get(0); // TODO: Change to use real genotype field
          genotype = genotype.replaceAll("[\\\\|0]", "");
          if (!genotype.isEmpty()) {
            samplesWithVariant.add(call.getCallSetName());
          }
        }
        LOG.info("Variant " + variant.getId() + " has " + samplesWithVariant.size() + " actual calls");

        for (String s1 : samplesWithVariant) {
          for (String s2 : samplesWithVariant) {
            // Reduce the emit size by half because our resulting matrix will be symmetric
            if (s1.compareTo(s2) < 0) {
              String key = s1 + "-" + s2;
              Integer count = counts.get(key);
              counts.put(key, count == null ? 1 : count + 1);
            }
          }
        }
      }
    }

    @Override
    public void endSlice() {
      LOG.info("Total map keys " + counts.size());
      for (Map.Entry<String, Integer> entry : counts.entrySet()) {
        emit(entry.getKey(), entry.getValue());
      }

      // This mapper will be serialized, so we clear out all the counts prematurely
      // so that our object doesn't get too large
      counts.clear();
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