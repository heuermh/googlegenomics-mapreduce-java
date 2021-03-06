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

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Lists;
import com.google.api.client.util.Maps;
import com.google.appengine.tools.cloudstorage.*;
import com.google.appengine.tools.pipeline.util.Pair;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class PcaServlet extends HttpServlet {

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String bucket = req.getParameter("bucket");
    String filename = req.getParameter("filename");

    GcsService gcsService =
        GcsServiceFactory.createGcsService(RetryParams.getDefaultInstance());

    // TODO: Use a prefetching read channel.
    // This is currently failing with 'invalid stream header'
    //  GcsInputChannel readChannel = gcsService.openPrefetchingReadChannel(
    //      new GcsFilename(bucket, filename), 0, 1024 * 1024);

    BiMap<String, Integer> callsetIndicies = HashBiMap.create();
    Map<Pair<Integer, Integer>, Integer> callsetData = Maps.newHashMap();

    // TODO: This gcs file can't be read when deployed locally
    GcsFilename fileName = new GcsFilename(bucket, filename);
    int fileSize = (int) gcsService.getMetadata(fileName).getLength();

    ByteBuffer result = ByteBuffer.allocate(fileSize);
    GcsInputChannel readChannel = gcsService.openReadChannel(fileName, 0);
    readChannel.read(result);
    readChannel.close();

    // Parse file
    String file = new String(result.array());
    for (String line : file.split(":")) {
      String[] data = line.split("-");
      if (data.length < 3) {
        continue;
      }
      int callset1 = getCallsetIndex(callsetIndicies, data[0]);
      int callset2 = getCallsetIndex(callsetIndicies, data[1]);
      Integer similarity = Integer.valueOf(data[2]);
      callsetData.put(Pair.of(callset1, callset2), similarity);
    }

    // Create matrix data
    int callsetCount = callsetIndicies.size();
    double[][] matrixData = new double[callsetCount][callsetCount];
    for (Map.Entry<Pair<Integer, Integer>, Integer> entry : callsetData.entrySet()) {
      Integer c1 = entry.getKey().getFirst();
      Integer c2 = entry.getKey().getSecond();
      matrixData[c1][c2] = entry.getValue();
      matrixData[c2][c1] = entry.getValue(); // The matrix is symmetric
    }

    for (int i = 0; i < callsetCount; i++) {
      matrixData[i][i] = callsetCount; // TODO: Do we need this?
    }

    List<GraphResult> results = getPcaData(matrixData, callsetIndicies.inverse());

    resp.setContentType("application/json");
    JsonGenerator jsonGenerator = JacksonFactory.getDefaultInstance().createJsonGenerator(resp.getWriter());
    jsonGenerator.serialize(results);
    jsonGenerator.flush();

  }

  private int getCallsetIndex(Map<String, Integer> callsetIndicies, String callsetName) {
    if (!callsetIndicies.containsKey(callsetName)) {
      callsetIndicies.put(callsetName, callsetIndicies.size());
    }
    return callsetIndicies.get(callsetName);
  }

  private static class GraphResult {
    @com.google.api.client.util.Key
    public String name;

    @com.google.api.client.util.Key
    public double graphX;

    @com.google.api.client.util.Key
    public double graphY;

    public GraphResult(String name, double x, double y) {
      this.name = name;
      this.graphX = Math.floor(x * 100) / 100;
      this.graphY = Math.floor(y * 100) / 100;
    }
  }

  // Convert the similarity matrix to an Eigen matrix.
  private List<GraphResult> getPcaData(double[][] data, BiMap<Integer, String> callsetNames) throws IOException {
    int rows = data.length;
    int cols = data.length;

    // Center the similarity matrix.
    double matrixSum = 0;
    double[] rowSums = new double[rows];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        matrixSum += data[i][j];
        rowSums[i] += data[i][j];
      }
    }
    double matrixMean = matrixSum / rows / cols;
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        double rowMean = rowSums[i] / rows;
        double colMean = rowSums[j] / rows;
        data[i][j] = data[i][j] - rowMean - colMean + matrixMean;
      }
    }


    // Determine the eigenvectors, and scale them so that their
    // sum of squares equals their associated eigenvalue.
    Matrix matrix = new Matrix(data);
    EigenvalueDecomposition eig = matrix.eig();
    Matrix eigenvectors = eig.getV();
    double[] realEigenvalues = eig.getRealEigenvalues();

    for (int j = 0; j < eigenvectors.getColumnDimension(); j++) {
      double sumSquares = 0;
      for (int i = 0; i < eigenvectors.getRowDimension(); i++) {
        sumSquares += eigenvectors.get(i, j) * eigenvectors.get(i, j);
      }
      for (int i = 0; i < eigenvectors.getRowDimension(); i++) {
        eigenvectors.set(i, j, eigenvectors.get(i,j) * Math.sqrt(realEigenvalues[j] / sumSquares));
      }
    }


    // Find the indices of the top two eigenvalues.
    int maxIndex = -1;
    int secondIndex = -1;
    double maxEigenvalue = 0;
    double secondEigenvalue = 0;

    for (int i = 0; i < realEigenvalues.length; i++) {
      double eigenvector = realEigenvalues[i];
      if (eigenvector > maxEigenvalue) {
        secondEigenvalue = maxEigenvalue;
        secondIndex = maxIndex;
        maxEigenvalue = eigenvector;
        maxIndex = i;
      } else if (eigenvector > secondEigenvalue) {
        secondEigenvalue = eigenvector;
        secondIndex = i;
      }
    }


    // Return projected data
    List<GraphResult> results = Lists.newArrayList();
    for (int i = 0; i < rows; i++) {
      results.add(new GraphResult(callsetNames.get(i),
          eigenvectors.get(i, maxIndex), eigenvectors.get(i, secondIndex)));
    }

    return results;
  }
}