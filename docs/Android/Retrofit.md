Retrofit是一个在安卓平台上流行使用的声明网络请求接口的库。它提供了良好的适配拓展，使得其能够不仅良好地声明静态接口，还可以高效获得各种请求类型、返回数据结果的适配支持。

## 接口声明

在`Retrofit`中，接口以`interface`结合注解来声明。见`retrofit2.http`包，它包含了许多注解用于描述接口。从Http的方法，到路径、参数、Header、以及表单格式等支持。

注意这些注解都是`Runtime`保留的，因为Retrofit依赖运行时解析注解。而非`apt`等方式。

## 动态代理

使用`retrofit.create(Class)`来创造接口实现实例时，Retrofit使用了动态代理。

```java
  public <T> T create(final Class<T> service) {
    validateServiceInterface(service);
    return (T)
        Proxy.newProxyInstance(
            service.getClassLoader(),
            new Class<?>[] {service},
            new InvocationHandler() {
              private final Object[] emptyArgs = new Object[0];

              @Override
              public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args)
                  throws Throwable {
                // If the method is a method from Object then defer to normal invocation.
                if (method.getDeclaringClass() == Object.class) {
                  return method.invoke(this, args);
                }
                args = args != null ? args : emptyArgs;
                Platform platform = Platform.get();
                return platform.isDefaultMethod(method)
                    ? platform.invokeDefaultMethod(method, service, proxy, args)
                    : loadServiceMethod(method).invoke(args);
              }
            });
  }
```
即对该接口的方法调用会分为三种情况：

- Object类方法，以此匿名`InvocationHandler`作为对象直接执行。
- 默认方法，对于高版本的java可以对接口提供默认实现。若是这种情况，则调用默认方法实现。
- 声明的接口方法，这是核心功能所在。过程如下。

先获取此`method`对象对应的注解解析结果包装对象，优先使用缓存的，否则首次解析此对象。其过程分为：方法注解解析，方法参数注解解析，返回类型解析。

其中，方法参数解析时，如果发现有参数是`Continuation`类型，则认为是`suspend`方法。最后会根据是否是`suspend`方法，给出不同的封装对象，里面包含了`requestFactory`，`callAdapter`。`responseConverter`

```java
    Converter<ResponseBody, ResponseT> responseConverter =
        createResponseConverter(retrofit, method, responseType);

    okhttp3.Call.Factory callFactory = retrofit.callFactory;
    if (!isKotlinSuspendFunction) {
      return new CallAdapted<>(requestFactory, callFactory, responseConverter, callAdapter);
    } else if (continuationWantsResponse) {
      //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
      return (HttpServiceMethod<ResponseT, ReturnT>)
          new SuspendForResponse<>(
              requestFactory,
              callFactory,
              responseConverter,
              (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter);
    } else {
      //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
      return (HttpServiceMethod<ResponseT, ReturnT>)
          new SuspendForBody<>(
              requestFactory,
              callFactory,
              responseConverter,
              (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter,
              continuationBodyNullable);
    }
```
下面来看具体调用。

```java
  final @Nullable ReturnT invoke(Object[] args) {
    Call<ResponseT> call = new OkHttpCall<>(requestFactory, args, callFactory, responseConverter);
    return adapt(call, args);
  }

    //对于suspend
    protected Object adapt(Call<ResponseT> call, Object[] args) {
      call = callAdapter.adapt(call);

      // Continuation必然是最后一个参数。
      Continuation<Response<ResponseT>> continuation =
          (Continuation<Response<ResponseT>>) args[args.length - 1];

      // See SuspendForBody for explanation about this try/catch.
      try {
        return KotlinExtensions.awaitResponse(call, continuation);
      } catch (Exception e) {
        return KotlinExtensions.suspendAndThrow(e, continuation);
      }
    }

// OkHttpCall转协程。即call入列，完成时发送resume。
suspend fun <T> Call<T>.awaitResponse(): Response<T> {
  return suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
      cancel()
    }
    enqueue(object : Callback<T> {
      override fun onResponse(call: Call<T>, response: Response<T>) {
        continuation.resume(response)
      }

      override fun onFailure(call: Call<T>, t: Throwable) {
        continuation.resumeWithException(t)
      }
    })
  }
}

    //对于普通的直接使用callAdapter完成。
    @Override
    protected ReturnT adapt(Call<ResponseT> call, Object[] args) {
      return callAdapter.adapt(call);
    }
```


## CallAdapter

若提供自定义的“请求调用”对象，比如除了内置的`retrofit2.Call`，要提供java的`CompletableFuture`作为接口方法返回类型。则需要提供`CallAdapter.Factory`。如下：
```java
public final class Java8CallAdapterFactory extends CallAdapter.Factory {
    // CallAdapter.Factory的抽象方法需要实现。即提供CallAdapter实例
  @Override
  public @Nullable CallAdapter<?, ?> get(
      Type returnType, Annotation[] annotations, Retrofit retrofit) {
          // 确认返回类型是要处理的类型
    if (getRawType(returnType) != CompletableFuture.class) {
      return null;
    }
        // 确认CompletableFuture包含了类型参数，即泛型有传入
    if (!(returnType instanceof ParameterizedType)) {
      throw new IllegalStateException(
          "CompletableFuture return type must be parameterized"
              + " as CompletableFuture<Foo> or CompletableFuture<? extends Foo>");
    }
    Type innerType = getParameterUpperBound(0, (ParameterizedType) returnType);

        // 若泛型不是`retrofit2.Response`，则使用BodyCallAdapter此自定义CallAdapter
    if (getRawType(innerType) != Response.class) {
      return new BodyCallAdapter<>(innerType);
    }

    // 若泛型是`retrofit2.Response`，则确认它的泛型参数有传入
    if (!(innerType instanceof ParameterizedType)) {
      throw new IllegalStateException(
          "Response must be parameterized" + " as Response<Foo> or Response<? extends Foo>");
    }
    // 获取Response的参数类型，构造ResponseCallAdapter
    Type responseType = getParameterUpperBound(0, (ParameterizedType) innerType);
    return new ResponseCallAdapter<>(responseType);
  }

  private static final class BodyCallAdapter<R> implements CallAdapter<R, CompletableFuture<R>> {
    private final Type responseType;

    BodyCallAdapter(Type responseType) {
      this.responseType = responseType;
    }

    // CallAdapter需要实现的方法，返回数据结果类型。即具体的数据实体类。SomeData对应的类型
    @Override
    public Type responseType() {
      return responseType;
    }

    // CallAdapter需要实现的方法，把retorif2.Call转为CompletableFuture即自定义的请求封装结构。
    @Override
    public CompletableFuture<R> adapt(final Call<R> call) {
      final CompletableFuture<R> future =
          new CompletableFuture<R>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
              if (mayInterruptIfRunning) {
                call.cancel();
              }
              return super.cancel(mayInterruptIfRunning);
            }
          };

      call.enqueue(
          new Callback<R>() {
            @Override
            public void onResponse(Call<R> call, Response<R> response) {
              if (response.isSuccessful()) {

                  //这里使用call.body
                future.complete(response.body());
              } else {
                future.completeExceptionally(new HttpException(response));
              }
            }

            @Override
            public void onFailure(Call<R> call, Throwable t) {
              future.completeExceptionally(t);
            }
          });

      return future;
    }
  }

// 大部分同上，这里只保留了response作为结果。
  private static final class ResponseCallAdapter<R>
      implements CallAdapter<R, CompletableFuture<Response<R>>> {
    private final Type responseType;
    @Override
    public CompletableFuture<Response<R>> adapt(final Call<R> call) {
      call.enqueue(
          new Callback<R>() {
            @Override
            public void onResponse(Call<R> call, Response<R> response) {
              future.complete(response);
            }
          });
      return future;
    }
  }
}
```

## CallFactory

在这里先得说它和OkHttp的关系是多么紧密。

### OkHttp关联

虽然它有`retrofit2.Call`，其在`retroft`中的内置实现其实是`OkHttpCall`顾名思义，用OkHttp实现了`retrofit2.Call`，此实现是无法被替换的。我们能替换的只有`okhttp3.Call.Factory`，它定义如下：
```java
  interface Factory {
    Call newCall(Request request);
  }
```
即我们需要这些OkHttp的类来构造一个`okhttp`的`Call`接口实现，以此实现自定义请求过程。而`okhttp.OkHttpClient`实现了`Call.Factory`由此我们替换时，往往只需要构造一个自定义配置的`OkHttpClient`来替换即可，一般不需要深度重新实现`okhttp.Call`。

## Converter

如上面所说，对于不可替换的`OkHttpCall`，它使用`okhttp`的类来抽象请求访问和数据响应。此过程对应`okhttp`内的`Request`和`Response`。这两者自身没有任何对结构化数据的支持，这里需要工具封装。在`retrofit`中，便是通过`Converter`接口。在自定义的时候，只需要提供`Converter.Factory`的子类即可。代码如下：
```java
    /**
     * Returns a {@link Converter} for converting an HTTP response body to {@code type}, or null if
     * {@code type} cannot be handled by this factory. This is used to create converters for
     * response types such as {@code SimpleResponse} from a {@code Call<SimpleResponse>}
     * declaration.
     */
    public @Nullable Converter<ResponseBody, ?> responseBodyConverter(
        Type type, Annotation[] annotations, Retrofit retrofit) {
      return null;
    }

    /**
     * Returns a {@link Converter} for converting {@code type} to an HTTP request body, or null if
     * {@code type} cannot be handled by this factory. This is used to create converters for types
     * specified by {@link Body @Body}, {@link Part @Part}, and {@link PartMap @PartMap} values.
     */
    public @Nullable Converter<?, RequestBody> requestBodyConverter(
        Type type,
        Annotation[] parameterAnnotations,
        Annotation[] methodAnnotations,
        Retrofit retrofit) {
      return null;
    }
```
即返回空代表没法转换。对于Retrofit可以添加一组`Converter.Factory`由此可以向下继续查询。

## 拓展阅读

- [retrofit github](https://github.com/square/retrofit)