它是由Java 6 引入的用于发现服务实现的功能。我认为它实现了`Service Locator`设计模式。

它通过如下几个部分来工作：

- 声明API层，API层一般包含服务接口定义，以及涉及的数据类型。它将被应用和实现者依赖。
- 实现API层，实现者依赖API模块，提供服务接口的实现。最后在`resources/META-INF/services`目录中创建所实现的服务接口全量限定名作为文件名，内容是实现类的全量限定名。
- 应用层，依赖API模块，使用`ServiceLoader.load`等方法来查询实现，从而使用该服务。

另外服务提供者以扩展的形式安装，需要把其jar文件我们引入到应用程序类路径、Java 扩展类路径或用户定义的类路径中。若类路径里没有此项，将依然无法提供服务。

## 拓展阅读

- [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html)
- [Java-spi example](https://www.baeldung.com/java-spi)
- [Java-spi example github](https://github.com/eugenp/tutorials/tree/master/java-spi)
- “设计模式”section