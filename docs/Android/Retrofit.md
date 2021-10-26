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



## ResponseConverter

## 拓展阅读

- [retrofit github](https://github.com/square/retrofit)