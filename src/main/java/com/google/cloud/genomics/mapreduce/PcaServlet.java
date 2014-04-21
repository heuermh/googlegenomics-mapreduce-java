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
import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.api.client.util.Maps;
import com.google.appengine.tools.cloudstorage.*;
import com.google.appengine.tools.pipeline.util.Pair;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
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
      int callset1 = getCallsetIndex(callsetIndicies, data[0]);
      int callset2 = getCallsetIndex(callsetIndicies, data[1]);
      Integer similarity = Integer.valueOf(data[2]);
      callsetData.put(Pair.of(callset1, callset2), similarity);
    }

    // Create matrix data
    int callsetCount = callsetIndicies.size();
    double[][] matrixData = new double[callsetCount][callsetCount];
    for (Map.Entry<Pair<Integer, Integer>, Integer> entry : callsetData.entrySet()) {
      matrixData[entry.getKey().getFirst()][entry.getKey().getSecond()] = entry.getValue();
    }

    writePcaData(matrixData, callsetIndicies.inverse(), resp.getWriter());
  }

  private int getCallsetIndex(Map<String, Integer> callsetIndicies, String callsetName) {
    if (!callsetIndicies.containsKey(callsetName)) {
      callsetIndicies.put(callsetName, callsetIndicies.size());
    }
    return callsetIndicies.get(callsetName);
  }

  // Convert the similarity matrix to an Eigen matrix.
  private void writePcaData(double[][] data, BiMap<Integer, String> callsetNames, PrintWriter writer) {
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


    // Output projected data as json
    for (int i = 0; i < rows; i++) {
      String callsetName = callsetNames.get(i);

      String[] result = new String[] {callsetName,
          String.valueOf(eigenvectors.get(i, maxIndex)), String.valueOf(eigenvectors.get(i, secondIndex))};

      // TODO: format as json so that this can be used to make a graph
      writer.println(Joiner.on("\t").join(result));
    }

  }
}