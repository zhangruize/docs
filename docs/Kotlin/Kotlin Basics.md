> 此文档较早，仅做遗留产物

这里主要讲解Kotlin转为字节码的时候，尤其和Java相比，是如何实现它的一些语法特性的。

## 不需要类，可以像函数式、脚本一样运行

编译器自动按文件名生成了对应的class名的外层包装（final修饰）。定义的函数都会转为static的调用。由于有main作为主函数入口，所以实际上会类似Java的static void main入口。

## 参数默认值，可选入参，命名入参

这些特性算是在一个方案里实现的。无论一个方法有多少参数的默认值（此时已经成为可选入参，以及支持命名入参），都会在我们定义之外再自动生成一个方法。这个方法相比于普通的定义，多了一个int和object的入参。object的入参含义暂且没发现。但int是借助位来标识有哪些有效入参，因此若发现无效入参，则可以对其赋值为默认值。见下面的例子：

```java
//方法原型
test(p1:String = "def1", p2:String = "def2", p3:String = "def3")
//自动生成
test(p1, p2, p3, int, object)
//int从右到左，每一位都标识了第几个参数是否有效，1标识是有效入参，0标识无效入参，要使用默认值。
//最后为p1,p2,p3完成初始赋值后，调用真正的函数原型test(p1,p2,p3)
```

## == vs ===

`==`可以类比Object.equals()，而`===`可以类比指针引用是否相等。

## lambda

kotlin中的lambda实现和java完全不同。有多种情况：

- 如果是内联方法，则lambda代码块会被内联进入调用者，此时没有什么包装。
- 一般情况下，代码块会被包装为`kotlin.jvm.internal.Lambda`的子类，并根据所包含的参数情况实现`FunctionX`接口再进行传递。

