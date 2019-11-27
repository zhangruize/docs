# 手写RN

## JS引擎

在Android上可用的Js引擎（[讨论页面](https://stackoverflow.com/questions/8374016/how-to-execute-javascript-on-android)）有如下这些：

- JSCore(JSC)（关于JSC和WebKit和Chromium等有很多关联，具体的参考后期补充）。
- WebView
- Rhino
- V8

而V8对于Java层需要JNI才能使用，有一个封装库叫[J2V8](https://github.com/eclipsesource/j2v8)，选择使用了这个。仓库应该是在JCenter。最新可用需要查看这个[issue](https://github.com/eclipsesource/J2V8/issues/384)。

## Hello, World

万事皆从Hello, world开始。

## 草稿内容

我们目标是把如下的JSX以原生的形式生成出来。后期会再考虑更复杂的View以及事件处理等，以及视图的更新。

```Javascript
<App/>

-->

<Text>Hello, {props.name}</Text>

```

未来甚至会考虑Java和Js的互通调用。比如借助Java触发网络请求等

因为这里会涉及过多JS语言要写的基础库，比如借助Babel把JSX转化为普通的JS函数调用等，我们先进一步把问题转化为如下的形式：

```Javascript

render(){
    return createElement("App")
}

class App extends RNElement{
    render(){
        return createElement("Text",
            {
                children:props.name
            })
    }
}

//Text extends RNElement and it's a build-in element 

```

下面的问题，我们需要分层次进行考虑。

- 对于UI前端逻辑，即上面的最顶端的应用层，是使用JS所写。并且会最终转化为若干的JS函数调用。
- 当JS层从应用层代码走向了JS的库层时，这些元素创建API的调用需要考虑该如何将信息进一步处理或者传递给其他层：
  - 本层直接处理。那么JS库层第一步会形成一棵渲染的树。这棵树应是必须要生成的，至少是因为为了便于后面快速更新子节点。但也可以进一步转化为图形层的合成结果，甚至更进一步，直接转化为了图形API的调用清单。**但是**仔细思考一下，以React Native的角度来想，React只需要负责描述前端的样式，无论业务逻辑怎样，都会返回正确的视图样式，最后需要传递给不同平台的Native视图组件，最终的渲染流程也应该交给原生的视图系统，他们各自的算法去合成。而自己不需要过多干涉。我们暂且不深入讨论这种方式的利弊。但值得一提的是，**这里显然会有另一种方式**，就是如果JS层继续更进一步，把图形的合成也完成，最终转化为了图形系统的API调用。很明显的区别在于，前者（RN）对平台的要求是需要提供对应的原生组件，以及原生组件能够在原生系统上合成显示。后者方案对平台的要求是，支持图形API的直接使用（如OpenGL, EGL等）。其实后者的方案会更像**Flutter**的运作方式。
  - 传递给其他层处理。是的，其实这两点我们不做严格区分。或者目前文档的区分不够明确，传递给其他层处理也可以是JS的更深层，或者内置的C层或者Java层。内置的C层是指伴随JS引擎（一般是C/C++）一块包含的C/C++库，用于处理这些视图的数据。而使用内置的C层的一个**优点**是，可以减少针对跨平台所需要写的代码量。C层可以更大程度直接复用一些统一的数据处理。

上面，我们讨论了从React转到最终如何以Native的形式呈现进行了一番深度的探讨。虽然直接转化到图形api的调用看起来已经不太像Native的含义，但也算方案的各种可能性。

下面我们将选择一种方式进行实现。

由于没有很好的C的水平，也对V8拓展不够熟悉，所以这里暂时不考虑从C/C++层拓展库的功能（处理视图数据等）。后面仅仅是JS <--> Java之间的使用。

因为考虑到，比如每一个`createElement`最终都会转化到Java库层的API上，比如我们把`Text`元素对应到了Android的`TextView`上，又把很多其他JSX元素对应到各种Android原生的组件上，当我们想把自定义的Element也和不同平台对应起来时，这里实际上就需要各平台端的自定义模块/组件注册绑定功能。依靠这种思维，前面的这种JSX->Android的对应关系，实际上也都属于各个平台的拓展，只是这些算是内置的，属于我们库在各个平台上本应该提供的基本模块/组件。

所以，我们下面实际要思考的是React层和各个平台层的拓展、注册、绑定这种关系。那么我们来初步思考下，各个平台需要提供的模块应需要实现怎样的接口呢？

Module:

- module name(id/key的作用)

UiElementModule extends Module:

- boolean matchTargetTag(Element e) //返回是否是目标的JSX的Tag

- onUpdate()

Java库层提供的API：

- registerUiElementModule()
- registerModule()  //一般需要等待Js主动调用



/////2019 11 18

原生Module是什么，实际上是两类东西：

1. 可以在JSX使用的Tag。
2. 可以在JS代码调用的方法。

实际上两者对于原生Java而言没有区别