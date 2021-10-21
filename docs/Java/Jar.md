JAR 文件本质上是一个 zip 文件，其中包含一个可选的 META-INF 目录。

一般情况下，JAR 文件是 Java 类文件和/或资源的简单存档，它们用作应用程序和扩展的构建块，META-INF 目录（如果存在）用于存储包和扩展配置数据，包括安全性、版本控制、扩展和服务。

## 构建

JAR 文件可以通过命令行jar工具创建，也可以使用 Java 平台中的  java.util.jar API 创建。

## 清单文件

- META-INF/MANIFEST.MF

主要由“主属性”部分和“条目属性”部分。各部分由空行分隔。关于主属性的介绍参见拓展阅读。

## 签名

- META-INF/MANIFEST.MF
- META-INF/*.SF
- META-INF/*.DSA
- META-INF/*.RSA
- META-INF/SIG-*

可以使用jdk附带的jarsigner来结合指定keystore签名，而keystore则可以使用jdk附带的keystore工具来生成jks格式的keystore文件。

这里主要介绍*.SF和*.RSA的作用。先看一份样例：

```
# MANIFEST.MF

Manifest-Version: 1.0
Created-By: 1.8.0_302 (Temurin)

Name: pkg/Hello.class
SHA-256-Digest: +XXeGO7G7/2tQpgeNLtkHTwRHK9zIZ03uQi5NDYw7Gw=
```

```
# ANDROID.SF

Signature-Version: 1.0
SHA-256-Digest-Manifest-Main-Attributes: SSbKFlknndiEVdAPV1pvgPnoo+P/I
 pmGp6WsJEAIiGY=
SHA-256-Digest-Manifest: qKW1LZ+EenVhjLC7IjLc1gmaiTi7D1bSeqWkUdMJelc=
Created-By: 1.8.0_302 (Temurin)

Name: pkg/Hello.class
SHA-256-Digest: dg9A2AXOkh4Ta3mEkylcsnFi+pCduFJbfN6bJbZBFIc=
```

这里简述一下属性含义：

- {x}-Digest-Manifest-Main-Attributes: 对清单文件主属性部分摘要
- {x}-Digest-Manifest: 对清单文件整体的摘要
- {x}-Digest: 对某实体文件的摘要（此文件必须在清单文件中包含）
- x: 摘要算法标准名称

再简述一下验证过程：

- 首先对清单文件的摘要进行验证。即使用摘要算法对清单文件进行摘要、主属性进行摘要，比对是否和*.SF的对应属性匹配。
- 其次对清单文件里的文件实体进行摘要，检查是否和清单文件的对应摘要匹配，而*.SF的该文件条目摘要是指清单文件里该实体条目的摘要。

*.RSA是 *.SF文件的数字签名版本，即需要使用公钥解密后，对比其内容是否和实际的 *.SF一致。

## 拓展阅读

- [oracle docs](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File)
- [others](https://nelenkov.blogspot.com/2013/04/android-code-signing.html)