## 背景

我认为Compose的诞生，出于如下几个背景：
- 声明式UI得到追捧。前端长期的声明式框架思维习惯，以及React, Vue等库，都是很好的代表。
- Kotlin对DSL的友好支持，可以高效制定DSL。
- Android一直以来几乎都是命令式开发（除了视图绑定、数据绑定）。
  
## Compose

是一套旨在用声明式方式来构建原生UI的系统，有比较细致的分层架构。它目标应该是能替代现有的Android View系统，尽管如此，也提供了折中的方式，可以以`ComposeView:View`来引入到View的结构之中。甚至，在`ComposeView`之中，依然可以`addView(View)`以此来弥补一些Compose暂时不便支持的`View`。

从这个目的出发，它在Android的UI基础Framework即，chorographer, Canvas之上，构建了自己的接口系统。这一系列的新方式，对于之前熟悉`View/ViewGroup`接口的人来说，是全新的、陌生的。因为将不再有熟悉的`Text:View, LinearLayout:ViewGroup, View.setOnClickListener, Text.setText， ViewGroup.addView`等这些接口。取而代之的是全新的一套。

> 在一开始我以为Compose只是基于View的接口重新以DSL的方式组织，替代的是XML填充方式（虽然替代XML更早也有第三方的库）。后来才意识到Compose目标更加激进。是一个全新的方式来构建Android原生。甚至还有Compose For Desktop这种产物出现。

## 优劣

按[官方文档](https://developer.android.com/jetpack/compose/ergonomics)说明，使用Compose的优点在于更小的包体积、更快的构建时间。这两点均得益于去除了布局XML资源的处理。此外，运行时性能也被认为更快，因为避免了XML的解析填充过程。

缺点：对于部分引入Compose后也会带来包增量。非捆绑库使得不能像View的接口在Zygote时有预加载。但是它附带了安装规则文件（`ProfileInstaller`），将有助于在安装apk时，进行AOT编译。帮助减少Compose启动时间和卡顿。

## 个人开发体验

使用Compose开发了音乐的页面，只涉及了基本的页面元素和交互，只从UI相关的角度来说，个人评价要点如下：
- 把UI的构建拆分到代码逻辑中，这种感觉对安卓来说是新颖的，有些类似前端的项目。
- Compose做到了声明式、做到了可组合，但在享受高效开发UI前，有很多学习成本需要克服。
- 界面Preview还比较繁琐，目前只Preview了很简单的元素，复杂页面的Preview易用性上目前还比不过Xml的tools命名空间。更多时候是无可视化，几乎只看代码来预测页面布局。
- 对Material支持友好，但若涉及广泛的UI，将有大量的学习成本。比如动画、列表、滚动、可绘制图像等等。因为失去了资源文件，意味着很多时候需要靠API来描述，而掌握这些API需要精力。
- 状态更新时需要小心，有一定学习成本。不小心的状态管理，会导致页面不断Recompose，而且几乎得不到任何提示，也一切运作良好。状态更新等方式需要习惯、适应。
- 比传统安卓开发更轻便简洁快速。就像我只想表达列表时，并不乐意为Recyclerview写那么多类一样。

## 瞎掰一句

Compose不仅是一个安卓上的选项，JetBrains把Compose也作为在多平台上推广Kotlin构建UI应用的方式。这给学习掌握Compose的人带来了更多的收益。截至目前，Compose多平台还处于alpha状态。不过未来也会是跨端方向的又一方案。这代表着，Compose/Flutter这两个安卓上较热门的跨端方案，将面临更复杂的关系。

我们不妨简单对比一下Compose和Flutter，在对外接口层，对开发者的体验其实就是，Kotlin的生态与Dart的生态，你更喜欢用Kotlin的Compose DSL规则来写页面，还是Dart的Flutter来以响应式构建页面。更下一层，Compose倾向于借助平台的原生能力，Flutter则是自带一个UI引擎。但鉴于Flutter的UI引擎是C++完成的，对于Compose来说依然可以支持，因此，这显得Flutter的上层，Dart/Dart VM处境略微尴尬。但Dart, Dart VM也号称是其设计能有助于更快进行这种UI树的重建。

我对Dart了解还不多，这里不多再瞎扯Dart和Kotlin的对比。


## 拓展阅读

- [Compose分层架构官网文档](https://developer.android.com/jetpack/compose/layering)，若关注如何实现，可以关注compose.ui包下内容。
- [ProfileInstaller介绍](https://developer.android.com/jetpack/androidx/releases/profileinstaller)
- [Compose多平台](https://github.com/JetBrains/compose-jb)