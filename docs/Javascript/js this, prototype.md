## this

- 在全局执行环境中（浏览器环境），`this`即`window`。
- 在函数内，`this`取值取决于函数调用方式：
    - 若在非严格模式下，没有设置`this`，则一般依然指向全局。对浏览器是`window`
    - 若在严格模式下，则会是`undefined`
    - 可以使用`call`, `apply`来设置函数内的`this`取值。此外还有ES5引入的`bind`
```js
// 对象可以作为 bind 或 apply 的第一个参数传递，并且该参数将绑定到该对象。
var obj = {a: 'Custom'};

// 声明一个变量，并将该变量作为全局对象 window 的属性。
var a = 'Global';

function whatsThis() {
  return this.a;  // this 的值取决于函数被调用的方式
}

whatsThis();          // 'Global' 因为在这个函数中 this 没有被设定，所以它默认为 全局/ window 对象
whatsThis.call(obj);  // 'Custom' 因为函数中的 this 被设置为obj
whatsThis.apply(obj); // 'Custom' 因为函数中的 this 被设置为obj

//另一个例子
function add(c, d) {
  return this.a + this.b + c + d;
}

var o = {a: 1, b: 3};

// 第一个参数是用作“this”的对象
// 其余参数用作函数的参数
add.call(o, 5, 7); // 16

//bind例子。它会创建一个相同函数体，但this永久被绑定为了bind的第一个参数。
function f(){
  return this.a;
}

var g = f.bind({a:"azerty"});
console.log(g()); // azerty

var h = g.bind({a:'yoo'}); // bind只生效一次！
console.log(h()); // azerty

var o = {a:37, f:f, g:g, h:h};
console.log(o.a, o.f(), o.g(), o.h()); // 37, 37, azerty, azerty

```
- 在类构造函数中，`this`原型会绑定该类所有的非静态方法（仅基类），若是派生类的构造函数中，则没有初始的this绑定，因为需要调用`super(..)`由此`this`依然是基类的情况。
```js
class Example {
  constructor() {
    const proto = Object.getPrototypeOf(this);
    console.log(Object.getOwnPropertyNames(proto));
  }
  first(){}
  second(){}
  static third(){}
}

new Example(); // ['constructor', 'first', 'second']
```
- 对象的方法中的`this`会被设置为该对象。它不受定义方法的方式影响，如下：
```js
var o = {
  prop: 37,
  f: function() {
    return this.prop;
  }
};

console.log(o.f()); // 37

//即使如下这样定义也可以
var o = {prop: 37};

function independent() {
  return this.prop;
}

o.f = independent;

console.log(o.f()); // 37

```


- 箭头函数的this和封闭词法环境的this保持一致。并且它不会被`call`, `apply`, `bind`改变。
```js
var globalObject = this;
var foo = (() => this);
console.log(foo() === globalObject); // true
// 作为对象的一个方法调用
var obj = {foo: foo};
console.log(obj.foo() === globalObject); // true

// 尝试使用call来设定this
console.log(foo.call(obj) === globalObject); // true

// 尝试使用bind来设定this
foo = foo.bind(obj);
console.log(foo() === globalObject); // true
```

## prototype



## 拓展阅读

- [mdn this](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Operators/this)