OkHttp是一个基于JDK的网络客户端。它因其拦截链的设计能被简单高效地定制拓展。结合其包含Connection的复用，对Http2的支持等，都使得它成为安卓开发中所被追捧使用的。它本身是不包含构建序列化、反序列化来发起请求、解析响应的工具的。而是只包含`Request`和`Response`的原始描述和功能。

## 拦截链

```kotlin
class RealInterceptorChain(
  internal val call: RealCall,
  private val interceptors: List<Interceptor>,
  private val index: Int,
  internal val exchange: Exchange?,
  internal val request: Request,
  internal val connectTimeoutMillis: Int,
  internal val readTimeoutMillis: Int,
  internal val writeTimeoutMillis: Int
) : Interceptor.Chain
```
在每次`process`的时候，都会`copy`其实就是更改下index和request，其他成员不变，构造出一个新的子`Chain`交给下个`Interceptor`处理。递归思想，且因为此实例相对轻量，这样子copy成本不大。
```kotlin
// 已精简
 override fun proceed(request: Request): Response {
    check(index < interceptors.size)
    // Call the next interceptor in the chain.
    val next = copy(index = index + 1, request = request)
    val interceptor = interceptors[index]

    val response = interceptor.intercept(next) ?: throw NullPointerException(
        "interceptor $interceptor returned null")
    return response
  }
```
这个设计模式本质上是方法的递归调用，递归的条件在于，遍历所有的处理器。

```java
class Processor{
    Result process(int index, Processor[] processors, Request request){
        if(index<processor.length){
            Processor p = processors[index]
            p.process(index+1, processors, request)
        }else{
            // no more processor, must give result now.
            throw Exception()
        }
    }
}
```
子类自定义Processor的时候，只需要复写`process`，若可以直接返回，则返回结果，若需要等待后面处理器的结果，则调用`super.process(...)`即可。但或许此方式不够优雅，我们可以把它封装为类似的`Chain`，而把`process`定义为`intercept`。`Chain`提供`process`方法来完成上面的内置递归逻辑。

若改写为循环方式
```
for(Processor p: processors){
    Result r = p.process(request)
    if(r.isOk) return r;
}
```
其实这样会无法构成链，即Processor无法获取后续Processor的处理结果并加以修改等。对特性是缺失的。而一旦依赖后续结果，势必还是使用递归更加简单。

## RealCall

```kotlin
// RealCall.kt 已精简
  internal fun getResponseWithInterceptorChain(): Response {
    // Build a full stack of interceptors.
    val interceptors = mutableListOf<Interceptor>()
    // 自定义的interceptor
    interceptors += client.interceptors
    // 失败时重试，或者code是重定向系列则发起新的请求。
    interceptors += RetryAndFollowUpInterceptor(client)
    interceptors += BridgeInterceptor(client.cookieJar)
    // 检查缓存。Http cache规范
    interceptors += CacheInterceptor(client.cache)
    // 负责连接，见后文
    interceptors += ConnectInterceptor
    if (!forWebSocket) {
        // 自定义的networkInterceptor
      interceptors += client.networkInterceptors
    }
    // 写入请求、读取响应处理者。
    interceptors += CallServerInterceptor(forWebSocket)

    val chain = RealInterceptorChain(...)
    try {
      val response = chain.proceed(originalRequest)
      if (isCanceled()) {
        response.closeQuietly()
        throw IOException("Canceled")
      }
      return response
    }
  }
```
## Connection创建和复用

在`ConnectInterceptor`拦截器中，会进入到`ExchangeFinder#findConnection`方法，来寻找一个可用的连接。它的策略已经体现在注释中：
```java
  /**
   * Returns a connection to host a new stream. This prefers the existing connection if it exists,
   * then the pool, finally building a new connection.
   * 即优先从call现有的connection取，其次从池里取，最后是建立新连接
   **/
```
`RealConnectionPool`是对应的池子维护者。` connections = ConcurrentLinkedQueue<RealConnection>()`是存储方式（并发集合请参阅“Java”section）。

创建连接的过程请见下文。

## Socket创建

连接建立是在`RealConnection`类核心方法`connect`:
```kotlin
// 已精简
  fun connect(...) {
    val connectionSpecs = route.address.connectionSpecs
    val connectionSpecSelector = ConnectionSpecSelector(connectionSpecs)

    if (route.address.sslSocketFactory == null) {
      val host = route.address.url.host
      if (!Platform.get().isCleartextTrafficPermitted(host)) {
        throw RouteException(UnknownServiceException(
            "CLEARTEXT communication to $host not permitted by network security policy"))
      }
    }

    while (true) {
        connectSocket(connectTimeout, readTimeout, call, eventListener)
        establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener)
        break
      }
    }
  }
```

这里会看是否有`sslSocketFactory`，若没有则意味着要进行明文传输，会检查一下平台的明文传输是否允许，不允许则异常。之后会连接套接字，建立协议层。这两个过程比较重要：
```kotlin
  private fun connectSocket(...) {
    val proxy = route.proxy
    val address = route.address

    val rawSocket = when (proxy.type()) {
      Proxy.Type.DIRECT, Proxy.Type.HTTP -> address.socketFactory.createSocket()!!
      else -> Socket(proxy)
    }
    this.rawSocket = rawSocket
      Platform.get().connectSocket(rawSocket, route.socketAddress, connectTimeout)

      source = rawSocket.source().buffer()
      sink = rawSocket.sink().buffer()
    }
  }
```
即根据代理情况从平台层创建`socket`，这里安卓对应的就是jdk的实现，后续会再使用平台层，连接`socket`并传入目标ip，再构造此`socket`的输入输出流。补充说明如下几点：

- 连接socket所使用的ip来自于`RouteSelector#resetNextInetSocketAddress`，而它最终会使用`OkHttpClient.dns`来查询。
    - `Dns.SYSTEM`是默认实现，借助了jdk的`InetAddress.getAllByName(hostname)`即可获取解析到的ip列表。
    - `DnsOverHttps`是HttpsDns协议的实现。它比传统的dns更加安全。因为传统的DNS可以被网络路径中的任意节点劫持、亦或者追踪，而此方式可以规避。详见“计算机网络”section.
- 此时的`Connection`还不是安全的。还没有构建协议。
- `socket`系列系统调用是操作系统功能之一，它也是文件描述符，支持常见的IO操作。详见“操作系统”section。

```kotlin
  private fun establishProtocol(
    connectionSpecSelector: ConnectionSpecSelector,
    pingIntervalMillis: Int,
    call: Call,
    eventListener: EventListener
  ) {
    if (route.address.sslSocketFactory == null) {
      if (Protocol.H2_PRIOR_KNOWLEDGE in route.address.protocols) {
        socket = rawSocket
        protocol = Protocol.H2_PRIOR_KNOWLEDGE
        startHttp2(pingIntervalMillis)
        return
      }

      socket = rawSocket
      protocol = Protocol.HTTP_1_1
      return
    }

    connectTls(connectionSpecSelector)

    if (protocol === Protocol.HTTP_2) {
      startHttp2(pingIntervalMillis)
    }
  }
```
建立协议的时候，若没有`sslSocketFactory`那么根据需要建立`http2`协议或`Http1.1`协议。若有`sslSocketFactory`则先建立`tls`连接，再按需建立`http2`协议。
```kotlin
  private fun connectTls(connectionSpecSelector: ConnectionSpecSelector) {
    val address = route.address
    val sslSocketFactory = address.sslSocketFactory
    var success = false
    var sslSocket: SSLSocket? = null
    try {
      // Create the wrapper over the connected socket.
      sslSocket = sslSocketFactory!!.createSocket(
          rawSocket, address.url.host, address.url.port, true /* autoClose */) as SSLSocket

      // Configure the socket's ciphers, TLS versions, and extensions.
      val connectionSpec = connectionSpecSelector.configureSecureSocket(sslSocket)
      if (connectionSpec.supportsTlsExtensions) {
        Platform.get().configureTlsExtensions(sslSocket, address.url.host, address.protocols)
      }

      // Force handshake. This can throw!
      sslSocket.startHandshake()
      // block for session establishment
      val sslSocketSession = sslSocket.session
      val unverifiedHandshake = sslSocketSession.handshake()

      // Verify that the socket's certificates are acceptable for the target host.
      if (!address.hostnameVerifier!!.verify(address.url.host, sslSocketSession)) {
        val peerCertificates = unverifiedHandshake.peerCertificates
        if (peerCertificates.isNotEmpty()) {
          val cert = peerCertificates[0] as X509Certificate
          throw SSLPeerUnverifiedException("""
              |Hostname ${address.url.host} not verified:
              |    certificate: ${CertificatePinner.pin(cert)}
              |    DN: ${cert.subjectDN.name}
              |    subjectAltNames: ${OkHostnameVerifier.allSubjectAltNames(cert)}
              """.trimMargin())
        } else {
          throw SSLPeerUnverifiedException(
              "Hostname ${address.url.host} not verified (no certificates)")
        }
      }

      val certificatePinner = address.certificatePinner!!

      handshake = Handshake(unverifiedHandshake.tlsVersion, unverifiedHandshake.cipherSuite,
          unverifiedHandshake.localCertificates) {
        certificatePinner.certificateChainCleaner!!.clean(unverifiedHandshake.peerCertificates,
            address.url.host)
      }

      // Check that the certificate pinner is satisfied by the certificates presented.
      certificatePinner.check(address.url.host) {
        handshake!!.peerCertificates.map { it as X509Certificate }
      }

      // Success! Save the handshake and the ALPN protocol.
      val maybeProtocol = if (connectionSpec.supportsTlsExtensions) {
        Platform.get().getSelectedProtocol(sslSocket)
      } else {
        null
      }
      socket = sslSocket
      source = sslSocket.source().buffer()
      sink = sslSocket.sink().buffer()
      protocol = if (maybeProtocol != null) Protocol.get(maybeProtocol) else Protocol.HTTP_1_1
      success = true
    } finally {
      if (sslSocket != null) {
        Platform.get().afterHandshake(sslSocket)
      }
      if (!success) {
        sslSocket?.closeQuietly()
      }
    }
  }
```
这里是`tsl`的建立过程，如果熟悉`Https`的握手过程，看到这里应该不会陌生。首先需要`sslSocketFactory.connectSocket`这里安卓也是使用jdk提供的实现，然后依靠`SSLSocket.startHandshake`完成`tsl`握手，它认可的证书来源于构造`SSLSocketFactory`的时候所传入的`TrustManager`，这些都属于jdk的范畴，详情见拓展阅读的`SSLSesion`，它会保存tsl会话中的各种关键信息包括本地的证书、对方的证书、建立的秘钥算法等。在此之后，OkHttp补充了几个额外的验证：

- `HostnameVerifer`，用于验证tsl会话中的对方证书是否被此主机接受。具体逻辑见`OkHostnameVerifer`。
- `CertificatePinner`，如果有固定证书器，则检查固定证书器需要检查目前的对方证书是否满足。

## 自定义Https

到此，再提一句关于自定义信任的问题，如[Stackoverflow](https://stackoverflow.com/questions/25509296/trusting-all-certificates-with-okhttp)上的回答，我们需要通过在构造`OkHttpClient`时传入使用自定义`TrustManager`构造的`SSLSocketFactory`、自定义的`HostnameVerifier`，以及可选的固定证书检查器即可。

## 自定义拦截器

常用于以下情景：

- 认证鉴权，比如添加通用的鉴权请求参数。
- 支持压缩编码格式
- 打印日志
- 自动化测试、Mock。

## Events

即可以添加`EventListener`来监听各个关键节点的事件回调。如图：

![](https://square.github.io/okhttp/images/events%402x.png)

## 拓展阅读

- [JDK SSLSesion](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/SSLSession.html)
- “计算机网络”section
- “操作系统”section