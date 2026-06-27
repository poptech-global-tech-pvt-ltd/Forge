package com.popclub.forgedriver.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.63.0)",
    comments = "Source: forge_driver.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ForgeDriverGrpc {

  private ForgeDriverGrpc() {}

  public static final java.lang.String SERVICE_NAME = "forgedriver.ForgeDriver";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PingRequest,
      com.popclub.forgedriver.proto.PingResponse> getPingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Ping",
      requestType = com.popclub.forgedriver.proto.PingRequest.class,
      responseType = com.popclub.forgedriver.proto.PingResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PingRequest,
      com.popclub.forgedriver.proto.PingResponse> getPingMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PingRequest, com.popclub.forgedriver.proto.PingResponse> getPingMethod;
    if ((getPingMethod = ForgeDriverGrpc.getPingMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getPingMethod = ForgeDriverGrpc.getPingMethod) == null) {
          ForgeDriverGrpc.getPingMethod = getPingMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.PingRequest, com.popclub.forgedriver.proto.PingResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Ping"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.PingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.PingResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("Ping"))
              .build();
        }
      }
    }
    return getPingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Tap",
      requestType = com.popclub.forgedriver.proto.TapRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapRequest, com.popclub.forgedriver.proto.ActionResponse> getTapMethod;
    if ((getTapMethod = ForgeDriverGrpc.getTapMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getTapMethod = ForgeDriverGrpc.getTapMethod) == null) {
          ForgeDriverGrpc.getTapMethod = getTapMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.TapRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Tap"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.TapRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("Tap"))
              .build();
        }
      }
    }
    return getTapMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByTextRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapByTextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TapByText",
      requestType = com.popclub.forgedriver.proto.TapByTextRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByTextRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapByTextMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByTextRequest, com.popclub.forgedriver.proto.ActionResponse> getTapByTextMethod;
    if ((getTapByTextMethod = ForgeDriverGrpc.getTapByTextMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getTapByTextMethod = ForgeDriverGrpc.getTapByTextMethod) == null) {
          ForgeDriverGrpc.getTapByTextMethod = getTapByTextMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.TapByTextRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TapByText"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.TapByTextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("TapByText"))
              .build();
        }
      }
    }
    return getTapByTextMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByCoordsRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapByCoordsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TapByCoords",
      requestType = com.popclub.forgedriver.proto.TapByCoordsRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByCoordsRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTapByCoordsMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TapByCoordsRequest, com.popclub.forgedriver.proto.ActionResponse> getTapByCoordsMethod;
    if ((getTapByCoordsMethod = ForgeDriverGrpc.getTapByCoordsMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getTapByCoordsMethod = ForgeDriverGrpc.getTapByCoordsMethod) == null) {
          ForgeDriverGrpc.getTapByCoordsMethod = getTapByCoordsMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.TapByCoordsRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TapByCoords"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.TapByCoordsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("TapByCoords"))
              .build();
        }
      }
    }
    return getTapByCoordsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTypeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Type",
      requestType = com.popclub.forgedriver.proto.TypeRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getTypeMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest, com.popclub.forgedriver.proto.ActionResponse> getTypeMethod;
    if ((getTypeMethod = ForgeDriverGrpc.getTypeMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getTypeMethod = ForgeDriverGrpc.getTypeMethod) == null) {
          ForgeDriverGrpc.getTypeMethod = getTypeMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.TypeRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Type"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.TypeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("Type"))
              .build();
        }
      }
    }
    return getTypeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getClearAndTypeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ClearAndType",
      requestType = com.popclub.forgedriver.proto.TypeRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getClearAndTypeMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.TypeRequest, com.popclub.forgedriver.proto.ActionResponse> getClearAndTypeMethod;
    if ((getClearAndTypeMethod = ForgeDriverGrpc.getClearAndTypeMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getClearAndTypeMethod = ForgeDriverGrpc.getClearAndTypeMethod) == null) {
          ForgeDriverGrpc.getClearAndTypeMethod = getClearAndTypeMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.TypeRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ClearAndType"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.TypeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("ClearAndType"))
              .build();
        }
      }
    }
    return getClearAndTypeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SwipeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getSwipeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Swipe",
      requestType = com.popclub.forgedriver.proto.SwipeRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SwipeRequest,
      com.popclub.forgedriver.proto.ActionResponse> getSwipeMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SwipeRequest, com.popclub.forgedriver.proto.ActionResponse> getSwipeMethod;
    if ((getSwipeMethod = ForgeDriverGrpc.getSwipeMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getSwipeMethod = ForgeDriverGrpc.getSwipeMethod) == null) {
          ForgeDriverGrpc.getSwipeMethod = getSwipeMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.SwipeRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Swipe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.SwipeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("Swipe"))
              .build();
        }
      }
    }
    return getSwipeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PressKeyRequest,
      com.popclub.forgedriver.proto.ActionResponse> getPressKeyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "PressKey",
      requestType = com.popclub.forgedriver.proto.PressKeyRequest.class,
      responseType = com.popclub.forgedriver.proto.ActionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PressKeyRequest,
      com.popclub.forgedriver.proto.ActionResponse> getPressKeyMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.PressKeyRequest, com.popclub.forgedriver.proto.ActionResponse> getPressKeyMethod;
    if ((getPressKeyMethod = ForgeDriverGrpc.getPressKeyMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getPressKeyMethod = ForgeDriverGrpc.getPressKeyMethod) == null) {
          ForgeDriverGrpc.getPressKeyMethod = getPressKeyMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.PressKeyRequest, com.popclub.forgedriver.proto.ActionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PressKey"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.PressKeyRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ActionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("PressKey"))
              .build();
        }
      }
    }
    return getPressKeyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.WaitPresentRequest,
      com.popclub.forgedriver.proto.WaitPresentResponse> getWaitPresentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WaitPresent",
      requestType = com.popclub.forgedriver.proto.WaitPresentRequest.class,
      responseType = com.popclub.forgedriver.proto.WaitPresentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.WaitPresentRequest,
      com.popclub.forgedriver.proto.WaitPresentResponse> getWaitPresentMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.WaitPresentRequest, com.popclub.forgedriver.proto.WaitPresentResponse> getWaitPresentMethod;
    if ((getWaitPresentMethod = ForgeDriverGrpc.getWaitPresentMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getWaitPresentMethod = ForgeDriverGrpc.getWaitPresentMethod) == null) {
          ForgeDriverGrpc.getWaitPresentMethod = getWaitPresentMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.WaitPresentRequest, com.popclub.forgedriver.proto.WaitPresentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WaitPresent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.WaitPresentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.WaitPresentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("WaitPresent"))
              .build();
        }
      }
    }
    return getWaitPresentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.IsPresentRequest,
      com.popclub.forgedriver.proto.IsPresentResponse> getIsPresentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "IsPresent",
      requestType = com.popclub.forgedriver.proto.IsPresentRequest.class,
      responseType = com.popclub.forgedriver.proto.IsPresentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.IsPresentRequest,
      com.popclub.forgedriver.proto.IsPresentResponse> getIsPresentMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.IsPresentRequest, com.popclub.forgedriver.proto.IsPresentResponse> getIsPresentMethod;
    if ((getIsPresentMethod = ForgeDriverGrpc.getIsPresentMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getIsPresentMethod = ForgeDriverGrpc.getIsPresentMethod) == null) {
          ForgeDriverGrpc.getIsPresentMethod = getIsPresentMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.IsPresentRequest, com.popclub.forgedriver.proto.IsPresentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "IsPresent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.IsPresentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.IsPresentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("IsPresent"))
              .build();
        }
      }
    }
    return getIsPresentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SourceRequest,
      com.popclub.forgedriver.proto.SourceResponse> getGetSourceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetSource",
      requestType = com.popclub.forgedriver.proto.SourceRequest.class,
      responseType = com.popclub.forgedriver.proto.SourceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SourceRequest,
      com.popclub.forgedriver.proto.SourceResponse> getGetSourceMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.SourceRequest, com.popclub.forgedriver.proto.SourceResponse> getGetSourceMethod;
    if ((getGetSourceMethod = ForgeDriverGrpc.getGetSourceMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getGetSourceMethod = ForgeDriverGrpc.getGetSourceMethod) == null) {
          ForgeDriverGrpc.getGetSourceMethod = getGetSourceMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.SourceRequest, com.popclub.forgedriver.proto.SourceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetSource"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.SourceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.SourceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("GetSource"))
              .build();
        }
      }
    }
    return getGetSourceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.ScreenshotRequest,
      com.popclub.forgedriver.proto.ScreenshotResponse> getScreenshotMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Screenshot",
      requestType = com.popclub.forgedriver.proto.ScreenshotRequest.class,
      responseType = com.popclub.forgedriver.proto.ScreenshotResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.ScreenshotRequest,
      com.popclub.forgedriver.proto.ScreenshotResponse> getScreenshotMethod() {
    io.grpc.MethodDescriptor<com.popclub.forgedriver.proto.ScreenshotRequest, com.popclub.forgedriver.proto.ScreenshotResponse> getScreenshotMethod;
    if ((getScreenshotMethod = ForgeDriverGrpc.getScreenshotMethod) == null) {
      synchronized (ForgeDriverGrpc.class) {
        if ((getScreenshotMethod = ForgeDriverGrpc.getScreenshotMethod) == null) {
          ForgeDriverGrpc.getScreenshotMethod = getScreenshotMethod =
              io.grpc.MethodDescriptor.<com.popclub.forgedriver.proto.ScreenshotRequest, com.popclub.forgedriver.proto.ScreenshotResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Screenshot"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ScreenshotRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.popclub.forgedriver.proto.ScreenshotResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ForgeDriverMethodDescriptorSupplier("Screenshot"))
              .build();
        }
      }
    }
    return getScreenshotMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ForgeDriverStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ForgeDriverStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ForgeDriverStub>() {
        @java.lang.Override
        public ForgeDriverStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ForgeDriverStub(channel, callOptions);
        }
      };
    return ForgeDriverStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ForgeDriverBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ForgeDriverBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ForgeDriverBlockingStub>() {
        @java.lang.Override
        public ForgeDriverBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ForgeDriverBlockingStub(channel, callOptions);
        }
      };
    return ForgeDriverBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ForgeDriverFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ForgeDriverFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ForgeDriverFutureStub>() {
        @java.lang.Override
        public ForgeDriverFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ForgeDriverFutureStub(channel, callOptions);
        }
      };
    return ForgeDriverFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void ping(com.popclub.forgedriver.proto.PingRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.PingResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPingMethod(), responseObserver);
    }

    /**
     */
    default void tap(com.popclub.forgedriver.proto.TapRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTapMethod(), responseObserver);
    }

    /**
     */
    default void tapByText(com.popclub.forgedriver.proto.TapByTextRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTapByTextMethod(), responseObserver);
    }

    /**
     */
    default void tapByCoords(com.popclub.forgedriver.proto.TapByCoordsRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTapByCoordsMethod(), responseObserver);
    }

    /**
     */
    default void type(com.popclub.forgedriver.proto.TypeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTypeMethod(), responseObserver);
    }

    /**
     */
    default void clearAndType(com.popclub.forgedriver.proto.TypeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getClearAndTypeMethod(), responseObserver);
    }

    /**
     */
    default void swipe(com.popclub.forgedriver.proto.SwipeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSwipeMethod(), responseObserver);
    }

    /**
     */
    default void pressKey(com.popclub.forgedriver.proto.PressKeyRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPressKeyMethod(), responseObserver);
    }

    /**
     */
    default void waitPresent(com.popclub.forgedriver.proto.WaitPresentRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.WaitPresentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getWaitPresentMethod(), responseObserver);
    }

    /**
     */
    default void isPresent(com.popclub.forgedriver.proto.IsPresentRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.IsPresentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getIsPresentMethod(), responseObserver);
    }

    /**
     */
    default void getSource(com.popclub.forgedriver.proto.SourceRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.SourceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetSourceMethod(), responseObserver);
    }

    /**
     */
    default void screenshot(com.popclub.forgedriver.proto.ScreenshotRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ScreenshotResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScreenshotMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ForgeDriver.
   */
  public static abstract class ForgeDriverImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ForgeDriverGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ForgeDriver.
   */
  public static final class ForgeDriverStub
      extends io.grpc.stub.AbstractAsyncStub<ForgeDriverStub> {
    private ForgeDriverStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ForgeDriverStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ForgeDriverStub(channel, callOptions);
    }

    /**
     */
    public void ping(com.popclub.forgedriver.proto.PingRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.PingResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void tap(com.popclub.forgedriver.proto.TapRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTapMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void tapByText(com.popclub.forgedriver.proto.TapByTextRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTapByTextMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void tapByCoords(com.popclub.forgedriver.proto.TapByCoordsRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTapByCoordsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void type(com.popclub.forgedriver.proto.TypeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTypeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void clearAndType(com.popclub.forgedriver.proto.TypeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getClearAndTypeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void swipe(com.popclub.forgedriver.proto.SwipeRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSwipeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void pressKey(com.popclub.forgedriver.proto.PressKeyRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPressKeyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void waitPresent(com.popclub.forgedriver.proto.WaitPresentRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.WaitPresentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getWaitPresentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void isPresent(com.popclub.forgedriver.proto.IsPresentRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.IsPresentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getIsPresentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getSource(com.popclub.forgedriver.proto.SourceRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.SourceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetSourceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void screenshot(com.popclub.forgedriver.proto.ScreenshotRequest request,
        io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ScreenshotResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getScreenshotMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ForgeDriver.
   */
  public static final class ForgeDriverBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ForgeDriverBlockingStub> {
    private ForgeDriverBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ForgeDriverBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ForgeDriverBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.popclub.forgedriver.proto.PingResponse ping(com.popclub.forgedriver.proto.PingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPingMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse tap(com.popclub.forgedriver.proto.TapRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTapMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse tapByText(com.popclub.forgedriver.proto.TapByTextRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTapByTextMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse tapByCoords(com.popclub.forgedriver.proto.TapByCoordsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTapByCoordsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse type(com.popclub.forgedriver.proto.TypeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTypeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse clearAndType(com.popclub.forgedriver.proto.TypeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getClearAndTypeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse swipe(com.popclub.forgedriver.proto.SwipeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSwipeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ActionResponse pressKey(com.popclub.forgedriver.proto.PressKeyRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPressKeyMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.WaitPresentResponse waitPresent(com.popclub.forgedriver.proto.WaitPresentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getWaitPresentMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.IsPresentResponse isPresent(com.popclub.forgedriver.proto.IsPresentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getIsPresentMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.SourceResponse getSource(com.popclub.forgedriver.proto.SourceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetSourceMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.popclub.forgedriver.proto.ScreenshotResponse screenshot(com.popclub.forgedriver.proto.ScreenshotRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScreenshotMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ForgeDriver.
   */
  public static final class ForgeDriverFutureStub
      extends io.grpc.stub.AbstractFutureStub<ForgeDriverFutureStub> {
    private ForgeDriverFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ForgeDriverFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ForgeDriverFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.PingResponse> ping(
        com.popclub.forgedriver.proto.PingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> tap(
        com.popclub.forgedriver.proto.TapRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTapMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> tapByText(
        com.popclub.forgedriver.proto.TapByTextRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTapByTextMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> tapByCoords(
        com.popclub.forgedriver.proto.TapByCoordsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTapByCoordsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> type(
        com.popclub.forgedriver.proto.TypeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTypeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> clearAndType(
        com.popclub.forgedriver.proto.TypeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getClearAndTypeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> swipe(
        com.popclub.forgedriver.proto.SwipeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSwipeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ActionResponse> pressKey(
        com.popclub.forgedriver.proto.PressKeyRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPressKeyMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.WaitPresentResponse> waitPresent(
        com.popclub.forgedriver.proto.WaitPresentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getWaitPresentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.IsPresentResponse> isPresent(
        com.popclub.forgedriver.proto.IsPresentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getIsPresentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.SourceResponse> getSource(
        com.popclub.forgedriver.proto.SourceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetSourceMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.popclub.forgedriver.proto.ScreenshotResponse> screenshot(
        com.popclub.forgedriver.proto.ScreenshotRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getScreenshotMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PING = 0;
  private static final int METHODID_TAP = 1;
  private static final int METHODID_TAP_BY_TEXT = 2;
  private static final int METHODID_TAP_BY_COORDS = 3;
  private static final int METHODID_TYPE = 4;
  private static final int METHODID_CLEAR_AND_TYPE = 5;
  private static final int METHODID_SWIPE = 6;
  private static final int METHODID_PRESS_KEY = 7;
  private static final int METHODID_WAIT_PRESENT = 8;
  private static final int METHODID_IS_PRESENT = 9;
  private static final int METHODID_GET_SOURCE = 10;
  private static final int METHODID_SCREENSHOT = 11;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PING:
          serviceImpl.ping((com.popclub.forgedriver.proto.PingRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.PingResponse>) responseObserver);
          break;
        case METHODID_TAP:
          serviceImpl.tap((com.popclub.forgedriver.proto.TapRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_TAP_BY_TEXT:
          serviceImpl.tapByText((com.popclub.forgedriver.proto.TapByTextRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_TAP_BY_COORDS:
          serviceImpl.tapByCoords((com.popclub.forgedriver.proto.TapByCoordsRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_TYPE:
          serviceImpl.type((com.popclub.forgedriver.proto.TypeRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_CLEAR_AND_TYPE:
          serviceImpl.clearAndType((com.popclub.forgedriver.proto.TypeRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_SWIPE:
          serviceImpl.swipe((com.popclub.forgedriver.proto.SwipeRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_PRESS_KEY:
          serviceImpl.pressKey((com.popclub.forgedriver.proto.PressKeyRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ActionResponse>) responseObserver);
          break;
        case METHODID_WAIT_PRESENT:
          serviceImpl.waitPresent((com.popclub.forgedriver.proto.WaitPresentRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.WaitPresentResponse>) responseObserver);
          break;
        case METHODID_IS_PRESENT:
          serviceImpl.isPresent((com.popclub.forgedriver.proto.IsPresentRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.IsPresentResponse>) responseObserver);
          break;
        case METHODID_GET_SOURCE:
          serviceImpl.getSource((com.popclub.forgedriver.proto.SourceRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.SourceResponse>) responseObserver);
          break;
        case METHODID_SCREENSHOT:
          serviceImpl.screenshot((com.popclub.forgedriver.proto.ScreenshotRequest) request,
              (io.grpc.stub.StreamObserver<com.popclub.forgedriver.proto.ScreenshotResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getPingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.PingRequest,
              com.popclub.forgedriver.proto.PingResponse>(
                service, METHODID_PING)))
        .addMethod(
          getTapMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.TapRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_TAP)))
        .addMethod(
          getTapByTextMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.TapByTextRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_TAP_BY_TEXT)))
        .addMethod(
          getTapByCoordsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.TapByCoordsRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_TAP_BY_COORDS)))
        .addMethod(
          getTypeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.TypeRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_TYPE)))
        .addMethod(
          getClearAndTypeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.TypeRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_CLEAR_AND_TYPE)))
        .addMethod(
          getSwipeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.SwipeRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_SWIPE)))
        .addMethod(
          getPressKeyMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.PressKeyRequest,
              com.popclub.forgedriver.proto.ActionResponse>(
                service, METHODID_PRESS_KEY)))
        .addMethod(
          getWaitPresentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.WaitPresentRequest,
              com.popclub.forgedriver.proto.WaitPresentResponse>(
                service, METHODID_WAIT_PRESENT)))
        .addMethod(
          getIsPresentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.IsPresentRequest,
              com.popclub.forgedriver.proto.IsPresentResponse>(
                service, METHODID_IS_PRESENT)))
        .addMethod(
          getGetSourceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.SourceRequest,
              com.popclub.forgedriver.proto.SourceResponse>(
                service, METHODID_GET_SOURCE)))
        .addMethod(
          getScreenshotMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.popclub.forgedriver.proto.ScreenshotRequest,
              com.popclub.forgedriver.proto.ScreenshotResponse>(
                service, METHODID_SCREENSHOT)))
        .build();
  }

  private static abstract class ForgeDriverBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ForgeDriverBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.popclub.forgedriver.proto.ForgeDriverProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ForgeDriver");
    }
  }

  private static final class ForgeDriverFileDescriptorSupplier
      extends ForgeDriverBaseDescriptorSupplier {
    ForgeDriverFileDescriptorSupplier() {}
  }

  private static final class ForgeDriverMethodDescriptorSupplier
      extends ForgeDriverBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ForgeDriverMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ForgeDriverGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ForgeDriverFileDescriptorSupplier())
              .addMethod(getPingMethod())
              .addMethod(getTapMethod())
              .addMethod(getTapByTextMethod())
              .addMethod(getTapByCoordsMethod())
              .addMethod(getTypeMethod())
              .addMethod(getClearAndTypeMethod())
              .addMethod(getSwipeMethod())
              .addMethod(getPressKeyMethod())
              .addMethod(getWaitPresentMethod())
              .addMethod(getIsPresentMethod())
              .addMethod(getGetSourceMethod())
              .addMethod(getScreenshotMethod())
              .build();
        }
      }
    }
    return result;
  }
}
