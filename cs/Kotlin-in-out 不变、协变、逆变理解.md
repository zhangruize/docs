in, out的使用对我一直有一些困惑，总觉得不如Java的`T extends xxx`, `T super Xxx`好用、好理解，但后来发现，`in, out`出现是为了弥补一些Java泛型带来的缺失之处的。

## in, out position

in和out是对应了不同的position的。对于in position，是指在方法参数之中出现的类型参数。对于out position，是指方法返回结果类型。也就是说：
- in/out用于修饰类型参数
- in/out修饰类型参数的位置不一样，in可以修饰方法入参，out可以修饰方法返回结果。此外，对于在interface, class声明的类型参数，in/out均可以使用。这也代表了后续对该类型参数所能使用的场景——方法入参或者返回结果。

## 解决了什么问题？

对于out，解决了如下的问题：
```
假定继承关系：Child:Parent:Any

interface OutExample<out T>{
    fun getSome():T
}

var a:OutExample<Child> = ...
var b:OutExample<Parent> = ...
b = a

```
而上述的情况在没有in/out的Java中则不允许。因为out表达了只是对外提供某类型，那么对于引用其父类型的实例，则是可以安全被子类型赋值的。如上面代码，对于`b.getSome()`预期得到的是`Parent`类型，赋值`a`后，得到的类型变为了`Child`，而`Child`是`Parent`子类，根据`多态`，依然可以正常访问`Parent`的所有特性。而这也被称作“协变”，它允许T的父类型参数作为泛型的类，被子类型的类所赋值。


对于in，解决了如下的问题（与此互补）：
```
interface InExample<in T>{
    fun readSome(t:T)
}
var a:InExample<Parent>=...
var b:InExample<Child>=...
a=b

```
上述的规则对于Java依然不允许。之所以这样的赋值是安全的，因为它依然满足面向对象的“多态”。这被称作“逆变”，它允许T的父类型参数的类赋值到子类型作为参数的类。因为子类型作为入参T，依旧满足“多态”，可以安全的访问父类特性。

## 多态

当单纯定义了泛型`T`时，若表示入参类型，则允许其子类作为类型。若表示出参，依然如此。

## 边界

类似Java，我们可以用`T extends superClass`定义一个带上边界的类型参数，[parent, childClass...]。