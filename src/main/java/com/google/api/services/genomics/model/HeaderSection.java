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
 * The header section of the BAM/SAM file.
 *
 * <p> This is the Java data model class that specifies how to parse/serialize into the JSON that is
 * transmitted over HTTP when working with the Genomics API. For a detailed explanation see:
 * <a href="http://code.google.com/p/google-http-java-client/wiki/JSON">http://code.google.com/p/google-http-java-client/wiki/JSON</a>
 * </p>
 *
 */
@SuppressWarnings("javadoc")
public final class HeaderSection extends com.google.api.client.json.GenericJson {

  /**
   * (@CO) One-line text comments.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<java.lang.String> comments;

  /**
   * The file uri that this data was imported from.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.lang.String fileUri;

  /**
   * (@HD) The header line.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<Header> headers;

  static {
    // hack to force ProGuard to consider Header used, since otherwise it would be stripped out
    // see http://code.google.com/p/google-api-java-client/issues/detail?id=528
    com.google.api.client.util.Data.nullOf(Header.class);
  }

  /**
   * (@PG) Programs.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<Program> programs;

  /**
   * (@RG) Read group.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<ReadGroup> readGroups;

  /**
   * (@SQ) Reference sequence dictionary.
   * The value may be {@code null}.
   */
  @com.google.api.client.util.Key
  private java.util.List<ReferenceSequence> refSequences;

  /**
   * (@CO) One-line text comments.
   * @return value or {@code null} for none
   */
  public java.util.List<java.lang.String> getComments() {
    return comments;
  }

  /**
   * (@CO) One-line text comments.
   * @param comments comments or {@code null} for none
   */
  public HeaderSection setComments(java.util.List<java.lang.String> comments) {
    this.comments = comments;
    return this;
  }

  /**
   * The file uri that this data was imported from.
   * @return value or {@code null} for none
   */
  public java.lang.String getFileUri() {
    return fileUri;
  }

  /**
   * The file uri that this data was imported from.
   * @param fileUri fileUri or {@code null} for none
   */
  public HeaderSection setFileUri(java.lang.String fileUri) {
    this.fileUri = fileUri;
    return this;
  }

  /**
   * (@HD) The header line.
   * @return value or {@code null} for none
   */
  public java.util.List<Header> getHeaders() {
    return headers;
  }

  /**
   * (@HD) The header line.
   * @param headers headers or {@code null} for none
   */
  public HeaderSection setHeaders(java.util.List<Header> headers) {
    this.headers = headers;
    return this;
  }

  /**
   * (@PG) Programs.
   * @return value or {@code null} for none
   */
  public java.util.List<Program> getPrograms() {
    return programs;
  }

  /**
   * (@PG) Programs.
   * @param programs programs or {@code null} for none
   */
  public HeaderSection setPrograms(java.util.List<Program> programs) {
    this.programs = programs;
    return this;
  }

  /**
   * (@RG) Read group.
   * @return value or {@code null} for none
   */
  public java.util.List<ReadGroup> getReadGroups() {
    return readGroups;
  }

  /**
   * (@RG) Read group.
   * @param readGroups readGroups or {@code null} for none
   */
  public HeaderSection setReadGroups(java.util.List<ReadGroup> readGroups) {
    this.readGroups = readGroups;
    return this;
  }

  /**
   * (@SQ) Reference sequence dictionary.
   * @return value or {@code null} for none
   */
  public java.util.List<ReferenceSequence> getRefSequences() {
    return refSequences;
  }

  /**
   * (@SQ) Reference sequence dictionary.
   * @param refSequences refSequences or {@code null} for none
   */
  public HeaderSection setRefSequences(java.util.List<ReferenceSequence> refSequences) {
    this.refSequences = refSequences;
    return this;
  }

  @Override
  public HeaderSection set(String fieldName, Object value) {
    return (HeaderSection) super.set(fieldName, value);
  }

  @Override
  public HeaderSection clone() {
    return (HeaderSection) super.clone();
  }

}
