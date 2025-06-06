/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/pubsub/v1/schema.proto

// Protobuf Java Version: 3.25.8
package com.google.pubsub.v1;

/**
 *
 *
 * <pre>
 * Response for the `ListSchemaRevisions` method.
 * </pre>
 *
 * Protobuf type {@code google.pubsub.v1.ListSchemaRevisionsResponse}
 */
public final class ListSchemaRevisionsResponse extends com.google.protobuf.GeneratedMessageV3
    implements
    // @@protoc_insertion_point(message_implements:google.pubsub.v1.ListSchemaRevisionsResponse)
    ListSchemaRevisionsResponseOrBuilder {
  private static final long serialVersionUID = 0L;

  // Use ListSchemaRevisionsResponse.newBuilder() to construct.
  private ListSchemaRevisionsResponse(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }

  private ListSchemaRevisionsResponse() {
    schemas_ = java.util.Collections.emptyList();
    nextPageToken_ = "";
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(UnusedPrivateParameter unused) {
    return new ListSchemaRevisionsResponse();
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.google.pubsub.v1.SchemaProto
        .internal_static_google_pubsub_v1_ListSchemaRevisionsResponse_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.pubsub.v1.SchemaProto
        .internal_static_google_pubsub_v1_ListSchemaRevisionsResponse_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.pubsub.v1.ListSchemaRevisionsResponse.class,
            com.google.pubsub.v1.ListSchemaRevisionsResponse.Builder.class);
  }

  public static final int SCHEMAS_FIELD_NUMBER = 1;

  @SuppressWarnings("serial")
  private java.util.List<com.google.pubsub.v1.Schema> schemas_;

  /**
   *
   *
   * <pre>
   * The revisions of the schema.
   * </pre>
   *
   * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
   */
  @java.lang.Override
  public java.util.List<com.google.pubsub.v1.Schema> getSchemasList() {
    return schemas_;
  }

  /**
   *
   *
   * <pre>
   * The revisions of the schema.
   * </pre>
   *
   * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
   */
  @java.lang.Override
  public java.util.List<? extends com.google.pubsub.v1.SchemaOrBuilder> getSchemasOrBuilderList() {
    return schemas_;
  }

  /**
   *
   *
   * <pre>
   * The revisions of the schema.
   * </pre>
   *
   * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
   */
  @java.lang.Override
  public int getSchemasCount() {
    return schemas_.size();
  }

  /**
   *
   *
   * <pre>
   * The revisions of the schema.
   * </pre>
   *
   * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
   */
  @java.lang.Override
  public com.google.pubsub.v1.Schema getSchemas(int index) {
    return schemas_.get(index);
  }

  /**
   *
   *
   * <pre>
   * The revisions of the schema.
   * </pre>
   *
   * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
   */
  @java.lang.Override
  public com.google.pubsub.v1.SchemaOrBuilder getSchemasOrBuilder(int index) {
    return schemas_.get(index);
  }

  public static final int NEXT_PAGE_TOKEN_FIELD_NUMBER = 2;

  @SuppressWarnings("serial")
  private volatile java.lang.Object nextPageToken_ = "";

  /**
   *
   *
   * <pre>
   * A token that can be sent as `page_token` to retrieve the next page.
   * If this field is empty, there are no subsequent pages.
   * </pre>
   *
   * <code>string next_page_token = 2;</code>
   *
   * @return The nextPageToken.
   */
  @java.lang.Override
  public java.lang.String getNextPageToken() {
    java.lang.Object ref = nextPageToken_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      nextPageToken_ = s;
      return s;
    }
  }

  /**
   *
   *
   * <pre>
   * A token that can be sent as `page_token` to retrieve the next page.
   * If this field is empty, there are no subsequent pages.
   * </pre>
   *
   * <code>string next_page_token = 2;</code>
   *
   * @return The bytes for nextPageToken.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString getNextPageTokenBytes() {
    java.lang.Object ref = nextPageToken_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b =
          com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      nextPageToken_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  private byte memoizedIsInitialized = -1;

  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    for (int i = 0; i < schemas_.size(); i++) {
      output.writeMessage(1, schemas_.get(i));
    }
    if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(nextPageToken_)) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, nextPageToken_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    for (int i = 0; i < schemas_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(1, schemas_.get(i));
    }
    if (!com.google.protobuf.GeneratedMessageV3.isStringEmpty(nextPageToken_)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, nextPageToken_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof com.google.pubsub.v1.ListSchemaRevisionsResponse)) {
      return super.equals(obj);
    }
    com.google.pubsub.v1.ListSchemaRevisionsResponse other =
        (com.google.pubsub.v1.ListSchemaRevisionsResponse) obj;

    if (!getSchemasList().equals(other.getSchemasList())) return false;
    if (!getNextPageToken().equals(other.getNextPageToken())) return false;
    if (!getUnknownFields().equals(other.getUnknownFields())) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (getSchemasCount() > 0) {
      hash = (37 * hash) + SCHEMAS_FIELD_NUMBER;
      hash = (53 * hash) + getSchemasList().hashCode();
    }
    hash = (37 * hash) + NEXT_PAGE_TOKEN_FIELD_NUMBER;
    hash = (53 * hash) + getNextPageToken().hashCode();
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      java.nio.ByteBuffer data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      byte[] data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      java.io.InputStream input) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      java.io.InputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(
        PARSER, input, extensionRegistry);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseDelimitedFrom(
      java.io.InputStream input) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseDelimitedFrom(
      java.io.InputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(
        PARSER, input, extensionRegistry);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      com.google.protobuf.CodedInputStream input) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(
        PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() {
    return newBuilder();
  }

  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }

  public static Builder newBuilder(com.google.pubsub.v1.ListSchemaRevisionsResponse prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }

  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }

  /**
   *
   *
   * <pre>
   * Response for the `ListSchemaRevisions` method.
   * </pre>
   *
   * Protobuf type {@code google.pubsub.v1.ListSchemaRevisionsResponse}
   */
  public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder>
      implements
      // @@protoc_insertion_point(builder_implements:google.pubsub.v1.ListSchemaRevisionsResponse)
      com.google.pubsub.v1.ListSchemaRevisionsResponseOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.google.pubsub.v1.SchemaProto
          .internal_static_google_pubsub_v1_ListSchemaRevisionsResponse_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.pubsub.v1.SchemaProto
          .internal_static_google_pubsub_v1_ListSchemaRevisionsResponse_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.pubsub.v1.ListSchemaRevisionsResponse.class,
              com.google.pubsub.v1.ListSchemaRevisionsResponse.Builder.class);
    }

    // Construct using com.google.pubsub.v1.ListSchemaRevisionsResponse.newBuilder()
    private Builder() {}

    private Builder(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
    }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      bitField0_ = 0;
      if (schemasBuilder_ == null) {
        schemas_ = java.util.Collections.emptyList();
      } else {
        schemas_ = null;
        schemasBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000001);
      nextPageToken_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.google.pubsub.v1.SchemaProto
          .internal_static_google_pubsub_v1_ListSchemaRevisionsResponse_descriptor;
    }

    @java.lang.Override
    public com.google.pubsub.v1.ListSchemaRevisionsResponse getDefaultInstanceForType() {
      return com.google.pubsub.v1.ListSchemaRevisionsResponse.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.pubsub.v1.ListSchemaRevisionsResponse build() {
      com.google.pubsub.v1.ListSchemaRevisionsResponse result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.pubsub.v1.ListSchemaRevisionsResponse buildPartial() {
      com.google.pubsub.v1.ListSchemaRevisionsResponse result =
          new com.google.pubsub.v1.ListSchemaRevisionsResponse(this);
      buildPartialRepeatedFields(result);
      if (bitField0_ != 0) {
        buildPartial0(result);
      }
      onBuilt();
      return result;
    }

    private void buildPartialRepeatedFields(
        com.google.pubsub.v1.ListSchemaRevisionsResponse result) {
      if (schemasBuilder_ == null) {
        if (((bitField0_ & 0x00000001) != 0)) {
          schemas_ = java.util.Collections.unmodifiableList(schemas_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.schemas_ = schemas_;
      } else {
        result.schemas_ = schemasBuilder_.build();
      }
    }

    private void buildPartial0(com.google.pubsub.v1.ListSchemaRevisionsResponse result) {
      int from_bitField0_ = bitField0_;
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.nextPageToken_ = nextPageToken_;
      }
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }

    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.setField(field, value);
    }

    @java.lang.Override
    public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }

    @java.lang.Override
    public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }

    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }

    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.pubsub.v1.ListSchemaRevisionsResponse) {
        return mergeFrom((com.google.pubsub.v1.ListSchemaRevisionsResponse) other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.pubsub.v1.ListSchemaRevisionsResponse other) {
      if (other == com.google.pubsub.v1.ListSchemaRevisionsResponse.getDefaultInstance())
        return this;
      if (schemasBuilder_ == null) {
        if (!other.schemas_.isEmpty()) {
          if (schemas_.isEmpty()) {
            schemas_ = other.schemas_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureSchemasIsMutable();
            schemas_.addAll(other.schemas_);
          }
          onChanged();
        }
      } else {
        if (!other.schemas_.isEmpty()) {
          if (schemasBuilder_.isEmpty()) {
            schemasBuilder_.dispose();
            schemasBuilder_ = null;
            schemas_ = other.schemas_;
            bitField0_ = (bitField0_ & ~0x00000001);
            schemasBuilder_ =
                com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders
                    ? getSchemasFieldBuilder()
                    : null;
          } else {
            schemasBuilder_.addAllMessages(other.schemas_);
          }
        }
      }
      if (!other.getNextPageToken().isEmpty()) {
        nextPageToken_ = other.nextPageToken_;
        bitField0_ |= 0x00000002;
        onChanged();
      }
      this.mergeUnknownFields(other.getUnknownFields());
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10:
              {
                com.google.pubsub.v1.Schema m =
                    input.readMessage(com.google.pubsub.v1.Schema.parser(), extensionRegistry);
                if (schemasBuilder_ == null) {
                  ensureSchemasIsMutable();
                  schemas_.add(m);
                } else {
                  schemasBuilder_.addMessage(m);
                }
                break;
              } // case 10
            case 18:
              {
                nextPageToken_ = input.readStringRequireUtf8();
                bitField0_ |= 0x00000002;
                break;
              } // case 18
            default:
              {
                if (!super.parseUnknownField(input, extensionRegistry, tag)) {
                  done = true; // was an endgroup tag
                }
                break;
              } // default:
          } // switch (tag)
        } // while (!done)
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.unwrapIOException();
      } finally {
        onChanged();
      } // finally
      return this;
    }

    private int bitField0_;

    private java.util.List<com.google.pubsub.v1.Schema> schemas_ =
        java.util.Collections.emptyList();

    private void ensureSchemasIsMutable() {
      if (!((bitField0_ & 0x00000001) != 0)) {
        schemas_ = new java.util.ArrayList<com.google.pubsub.v1.Schema>(schemas_);
        bitField0_ |= 0x00000001;
      }
    }

    private com.google.protobuf.RepeatedFieldBuilderV3<
            com.google.pubsub.v1.Schema,
            com.google.pubsub.v1.Schema.Builder,
            com.google.pubsub.v1.SchemaOrBuilder>
        schemasBuilder_;

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public java.util.List<com.google.pubsub.v1.Schema> getSchemasList() {
      if (schemasBuilder_ == null) {
        return java.util.Collections.unmodifiableList(schemas_);
      } else {
        return schemasBuilder_.getMessageList();
      }
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public int getSchemasCount() {
      if (schemasBuilder_ == null) {
        return schemas_.size();
      } else {
        return schemasBuilder_.getCount();
      }
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public com.google.pubsub.v1.Schema getSchemas(int index) {
      if (schemasBuilder_ == null) {
        return schemas_.get(index);
      } else {
        return schemasBuilder_.getMessage(index);
      }
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder setSchemas(int index, com.google.pubsub.v1.Schema value) {
      if (schemasBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureSchemasIsMutable();
        schemas_.set(index, value);
        onChanged();
      } else {
        schemasBuilder_.setMessage(index, value);
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder setSchemas(int index, com.google.pubsub.v1.Schema.Builder builderForValue) {
      if (schemasBuilder_ == null) {
        ensureSchemasIsMutable();
        schemas_.set(index, builderForValue.build());
        onChanged();
      } else {
        schemasBuilder_.setMessage(index, builderForValue.build());
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder addSchemas(com.google.pubsub.v1.Schema value) {
      if (schemasBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureSchemasIsMutable();
        schemas_.add(value);
        onChanged();
      } else {
        schemasBuilder_.addMessage(value);
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder addSchemas(int index, com.google.pubsub.v1.Schema value) {
      if (schemasBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureSchemasIsMutable();
        schemas_.add(index, value);
        onChanged();
      } else {
        schemasBuilder_.addMessage(index, value);
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder addSchemas(com.google.pubsub.v1.Schema.Builder builderForValue) {
      if (schemasBuilder_ == null) {
        ensureSchemasIsMutable();
        schemas_.add(builderForValue.build());
        onChanged();
      } else {
        schemasBuilder_.addMessage(builderForValue.build());
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder addSchemas(int index, com.google.pubsub.v1.Schema.Builder builderForValue) {
      if (schemasBuilder_ == null) {
        ensureSchemasIsMutable();
        schemas_.add(index, builderForValue.build());
        onChanged();
      } else {
        schemasBuilder_.addMessage(index, builderForValue.build());
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder addAllSchemas(java.lang.Iterable<? extends com.google.pubsub.v1.Schema> values) {
      if (schemasBuilder_ == null) {
        ensureSchemasIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(values, schemas_);
        onChanged();
      } else {
        schemasBuilder_.addAllMessages(values);
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder clearSchemas() {
      if (schemasBuilder_ == null) {
        schemas_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
      } else {
        schemasBuilder_.clear();
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public Builder removeSchemas(int index) {
      if (schemasBuilder_ == null) {
        ensureSchemasIsMutable();
        schemas_.remove(index);
        onChanged();
      } else {
        schemasBuilder_.remove(index);
      }
      return this;
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public com.google.pubsub.v1.Schema.Builder getSchemasBuilder(int index) {
      return getSchemasFieldBuilder().getBuilder(index);
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public com.google.pubsub.v1.SchemaOrBuilder getSchemasOrBuilder(int index) {
      if (schemasBuilder_ == null) {
        return schemas_.get(index);
      } else {
        return schemasBuilder_.getMessageOrBuilder(index);
      }
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public java.util.List<? extends com.google.pubsub.v1.SchemaOrBuilder>
        getSchemasOrBuilderList() {
      if (schemasBuilder_ != null) {
        return schemasBuilder_.getMessageOrBuilderList();
      } else {
        return java.util.Collections.unmodifiableList(schemas_);
      }
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public com.google.pubsub.v1.Schema.Builder addSchemasBuilder() {
      return getSchemasFieldBuilder().addBuilder(com.google.pubsub.v1.Schema.getDefaultInstance());
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public com.google.pubsub.v1.Schema.Builder addSchemasBuilder(int index) {
      return getSchemasFieldBuilder()
          .addBuilder(index, com.google.pubsub.v1.Schema.getDefaultInstance());
    }

    /**
     *
     *
     * <pre>
     * The revisions of the schema.
     * </pre>
     *
     * <code>repeated .google.pubsub.v1.Schema schemas = 1;</code>
     */
    public java.util.List<com.google.pubsub.v1.Schema.Builder> getSchemasBuilderList() {
      return getSchemasFieldBuilder().getBuilderList();
    }

    private com.google.protobuf.RepeatedFieldBuilderV3<
            com.google.pubsub.v1.Schema,
            com.google.pubsub.v1.Schema.Builder,
            com.google.pubsub.v1.SchemaOrBuilder>
        getSchemasFieldBuilder() {
      if (schemasBuilder_ == null) {
        schemasBuilder_ =
            new com.google.protobuf.RepeatedFieldBuilderV3<
                com.google.pubsub.v1.Schema,
                com.google.pubsub.v1.Schema.Builder,
                com.google.pubsub.v1.SchemaOrBuilder>(
                schemas_, ((bitField0_ & 0x00000001) != 0), getParentForChildren(), isClean());
        schemas_ = null;
      }
      return schemasBuilder_;
    }

    private java.lang.Object nextPageToken_ = "";

    /**
     *
     *
     * <pre>
     * A token that can be sent as `page_token` to retrieve the next page.
     * If this field is empty, there are no subsequent pages.
     * </pre>
     *
     * <code>string next_page_token = 2;</code>
     *
     * @return The nextPageToken.
     */
    public java.lang.String getNextPageToken() {
      java.lang.Object ref = nextPageToken_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        nextPageToken_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }

    /**
     *
     *
     * <pre>
     * A token that can be sent as `page_token` to retrieve the next page.
     * If this field is empty, there are no subsequent pages.
     * </pre>
     *
     * <code>string next_page_token = 2;</code>
     *
     * @return The bytes for nextPageToken.
     */
    public com.google.protobuf.ByteString getNextPageTokenBytes() {
      java.lang.Object ref = nextPageToken_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b =
            com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        nextPageToken_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    /**
     *
     *
     * <pre>
     * A token that can be sent as `page_token` to retrieve the next page.
     * If this field is empty, there are no subsequent pages.
     * </pre>
     *
     * <code>string next_page_token = 2;</code>
     *
     * @param value The nextPageToken to set.
     * @return This builder for chaining.
     */
    public Builder setNextPageToken(java.lang.String value) {
      if (value == null) {
        throw new NullPointerException();
      }
      nextPageToken_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }

    /**
     *
     *
     * <pre>
     * A token that can be sent as `page_token` to retrieve the next page.
     * If this field is empty, there are no subsequent pages.
     * </pre>
     *
     * <code>string next_page_token = 2;</code>
     *
     * @return This builder for chaining.
     */
    public Builder clearNextPageToken() {
      nextPageToken_ = getDefaultInstance().getNextPageToken();
      bitField0_ = (bitField0_ & ~0x00000002);
      onChanged();
      return this;
    }

    /**
     *
     *
     * <pre>
     * A token that can be sent as `page_token` to retrieve the next page.
     * If this field is empty, there are no subsequent pages.
     * </pre>
     *
     * <code>string next_page_token = 2;</code>
     *
     * @param value The bytes for nextPageToken to set.
     * @return This builder for chaining.
     */
    public Builder setNextPageTokenBytes(com.google.protobuf.ByteString value) {
      if (value == null) {
        throw new NullPointerException();
      }
      checkByteStringIsUtf8(value);
      nextPageToken_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }

    @java.lang.Override
    public final Builder setUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }

    // @@protoc_insertion_point(builder_scope:google.pubsub.v1.ListSchemaRevisionsResponse)
  }

  // @@protoc_insertion_point(class_scope:google.pubsub.v1.ListSchemaRevisionsResponse)
  private static final com.google.pubsub.v1.ListSchemaRevisionsResponse DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = new com.google.pubsub.v1.ListSchemaRevisionsResponse();
  }

  public static com.google.pubsub.v1.ListSchemaRevisionsResponse getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<ListSchemaRevisionsResponse> PARSER =
      new com.google.protobuf.AbstractParser<ListSchemaRevisionsResponse>() {
        @java.lang.Override
        public ListSchemaRevisionsResponse parsePartialFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
          Builder builder = newBuilder();
          try {
            builder.mergeFrom(input, extensionRegistry);
          } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw e.setUnfinishedMessage(builder.buildPartial());
          } catch (com.google.protobuf.UninitializedMessageException e) {
            throw e.asInvalidProtocolBufferException().setUnfinishedMessage(builder.buildPartial());
          } catch (java.io.IOException e) {
            throw new com.google.protobuf.InvalidProtocolBufferException(e)
                .setUnfinishedMessage(builder.buildPartial());
          }
          return builder.buildPartial();
        }
      };

  public static com.google.protobuf.Parser<ListSchemaRevisionsResponse> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<ListSchemaRevisionsResponse> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.pubsub.v1.ListSchemaRevisionsResponse getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }
}
