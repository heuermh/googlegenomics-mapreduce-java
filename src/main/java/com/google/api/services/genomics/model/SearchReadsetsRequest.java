/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://code.google.com/p/google-apis-client-generator/
 * Modify at your own risk.
 */

package com.google.api.services.genomics.model;

/**
 * The readset search request.
 *
 * <p> This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Genomics API. For a detailed explanation see:
 * <a href="http://code.google.com/p/google-http-java-client/wiki/JSON">http://code.google.com/p/google-http-java-client/wiki/JSON</a>
 * </p>
 *
 */
@SuppressWarnings("javadoc")
public final class SearchReadsetsRequest extends com.google.api.client.json.GenericJson {

  /**
   * Restricts this query to readsets within the given datasets. At least one ID must be provided.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<String> datasetIds;

  /**
   * The continuation token, which is used to page through large result sets. To get the next page
   * of results, set this parameter to the value of "nextPageToken" from the previous response.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private String pageToken;

  /**
   * Restricts this query to readsets within the given datasets. At least one ID must be provided.
   * @return value or {@code null} for none
   */
  public java.util.List<String> getDatasetIds() {
    return datasetIds;
  }

  /**
   * Restricts this query to readsets within the given datasets. At least one ID must be provided.
   * @param datasetIds datasetIds or {@code null} for none
   */
  public SearchReadsetsRequest setDatasetIds(java.util.List<String> datasetIds) {
    this.datasetIds = datasetIds;
    return this;
  }

  /**
   * The continuation token, which is used to page through large result sets. To get the next page
   * of results, set this parameter to the value of "nextPageToken" from the previous response.
   * @return value or {@code null} for none
   */
  public String getPageToken() {
    return pageToken;
  }

  /**
   * The continuation token, which is used to page through large result sets. To get the next page
   * of results, set this parameter to the value of "nextPageToken" from the previous response.
   * @param pageToken pageToken or {@code null} for none
   */
  public SearchReadsetsRequest setPageToken(String pageToken) {
    this.pageToken = pageToken;
    return this;
  }

  @Override
  public SearchReadsetsRequest set(String fieldName, Object value) {
    return (SearchReadsetsRequest) super.set(fieldName, value);
  }

  @Override
  public SearchReadsetsRequest clone() {
    return (SearchReadsetsRequest) super.clone();
  }

}
