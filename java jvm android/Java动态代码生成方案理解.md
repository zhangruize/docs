# Java动态生成代码理解

![](https://user-gold-cdn.xitu.io/2018/1/8/160d5259f6434023?imageView2/0/w/1280/h/960/format/webp/ignore-error/1)

APT

APT(Annotation Processing Too）应该都听过并且用过，概念比较好理解，主要作用在编译期，也是比较流行且常见的技术。
代码编译期解析注解后，结合square公司的开源项目javapoet项目，生成自定逻辑的java源码。有很多开源库都在用如：ButterKnife、EventBus等。

AspectJ

AspectJ支持编译期和加载时代码注入, 有一个专门的编译器用来生成遵守Java字节编码规范的Class文件。更多细节可以看这里。

Javassist

Javassist是一个开源的分析、编辑和创建Java字节码的类库。允许开发者自由的在一个已经编译好的类中添加新的方法，或者是修改已有的方法，允许开发者忽略被修改的类本身的细节和结构。
360开源插件项目RePlugin中，为了减少对安卓系统的Hook点，又希望解耦开发层代码逻辑，其中就用到了Javassist技术。

[参考](https://juejin.im/post/5a533c8df265da3e236651f3)