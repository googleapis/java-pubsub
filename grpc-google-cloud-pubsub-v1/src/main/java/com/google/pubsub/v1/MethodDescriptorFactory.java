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

package com.google.pubsub.v1;

import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;

/**
 * A utility class to create gRPC MethodDescriptors for SchemaService operations.
 */
public final class MethodDescriptorFactory {

    private MethodDescriptorFactory() {} // Prevent instantiation

    /**
     * Creates a MethodDescriptor for a unary gRPC method.
     *
     * @param serviceName The name of the service (e.g., "google.pubsub.v1.SchemaService").
     * @param methodName The name of the method (e.g., "CreateSchema").
     * @param requestType The request message type.
     * @param responseType The response message type.
     * @return A configured MethodDescriptor.
     */
    @SuppressWarnings("unchecked")
    public static <ReqT extends Message, RespT extends Message> MethodDescriptor<ReqT, RespT> createUnaryMethodDescriptor(
            String serviceName, String methodName, ReqT requestType, RespT responseType) {
        return MethodDescriptor.<ReqT, RespT>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceName, methodName))
                .setSampledToLocalTracing(true)
                .setRequestMarshaller(ProtoUtils.marshaller(requestType))
                .setResponseMarshaller(ProtoUtils.marshaller(responseType))
                .setSchemaDescriptor(new SchemaServiceGrpc.SchemaServiceMethodDescriptorSupplier(methodName))
                .build();
    }
}