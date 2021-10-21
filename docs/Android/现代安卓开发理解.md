## 背景

2021年，当我再次开始重新面对安卓应用开发时，我想忘掉之前的一切，即那些固有的知识，想尝试按照官方的最新建议指导，来开发一款应用会是什么体验。于是，我以`zrek app`为动机，开始广泛使用官方所推崇的最新的库。也为此付出了巨大的学习成本。所涉及到的Jetpack库包含：

- Navigation
- Paging
- Activity, Fragment
- DataStore
- Compose
- android-dagger(为了进一步了解本质，没有直接用hilt)
- ViewBinding

除了Jetpack的这些库，还有Kotlin协程的推广使用也带给我不少学习和适应成本，当然在图片加载这块，coil给了我惊喜。下面来分开描述

## Navigation

### 背景

导航库是比较新的玩意，目的在于提倡开发者去写nav_graph.xml，而避免去写复杂的FragmentManager的transaction。也进而规避掉复杂的Fragment生命周期带来的问题。此外还对DeepLink支持友好，对导航时传递参数友好。它方便于把这些导航方面的代码抽离、提炼，并以可视化直观的方式管理、便于更多项目维护者理解。也提供导航图的import，从而可以复用创造更复杂的图。

但导航自身是很宽泛的话题。长按出现了弹出菜单、抽屉、底部Tab、打开新页面、闪屏、甚至是View的出现和消失，这都多多少少可以算是导航。往大了说，是页面之间跳转，往小了说，甚至是一个页面内的小交互。

### 概述

Navigation包含了三个部分组成：

- nav_graph，一种新的xml资源。并提供了可视化的工具，用于定义导航的“目的地”，和action即导航路径，甚至包括路径所需要的参数传递，以及其他属性（比如对栈如何pop操作），似乎可以添加自定义属性。
- navHost。它扮演着发起导航的角色，目前一般是`NavigationHostFramgent`，navHost需要具备`navController`这个类将提供`navigate(...)`方法，来让我们传递在xml预定义的路径id、参数，从而按照对应路径进行跳转。
- navigator。对导航到目的地的行为抽象。它有多个实现子类，如`FragmentNavigator`, `ActivityNavigator`等。我们可以自定义目的地类型，并创建对应的`navigator`实现类。

其内部工作流程一般是，需要给`NavigationHostFramgent`即更宽泛说，给`navHost`指定一份`nav_graph`即导航图资源来解析。在调用`navHost`提供的`navController`的`navigate`方法时，需要传递此导航图内有效的路径id，以及所需参数。`navController`会根据id匹配出路径定义、找到目的地，再查询该目的地类型的`Navigator`，并使用`Navigator`传入所有上下文信息实现跳转。

尽管Navigation是想导航一切，是一个通用的解决方案。但若真的想在实际使用中支持各种大大小小的场景，是需要花费不小功夫的。

比如我为了在自定义的底部导航栏的多个Tab中，为了切换Fragment时复用所创建的，需要以自定义`FragmentNavigator`来完成，官方并没有这种操作的支持。`Navigator`是对导航到目的地的实现抽象，我们可以在`nav_graph.xml`定义各种各样的目的地，但必须也要实现这些目的地类型的`navigator`。

## Paging

### 概述

几乎对于所有的列表页面，都不可避免地需要使用分页数据。`Paging`则是官方给出的解决方案。它旨在提供一套开箱即用的分页工具，使用时只需要定义`PagingSource`，简单设置下`Pager`并得到分页flow，并对此`flow.collectLatest { PagingAdapter.submitData(..)}`，这里的PagingAdapter用作给RecyclerView的Adapter即可完成。

这个库涉及了kotlin `flow`的使用，也提供`RxJava`和`LiveData`的支持。其内部工作原理要稍复杂些：

- 在ViewModel中定义Pager，给定`PagingConfig`以及`PagingSource`后可以获得`flow`。通过`flow.collect`api来为`PagingAdapter`提交数据。
- `PagingAdapter`对下次获取分页数据的时机并非依赖于对列表可见条目位置的监听，而是根据`getItem`来判断。若`getItem`时传入的position和总item数量的差，已经小于了`PagingConfig`规定的阈值，则开始调用`PagingSource`拉取新的数据。
- 给`PagingAdapter`提交的数据类型是`PagingData`，其封装了具体的Paging事件，比如加载、刷新、异常。这些事件对于`LoadingHeader/Footer`是有用的。

## Activity/Fragment/ViewModel, Scope

### 概述

这里主要想说的是，在一两年前还觉得非常不错的`LiveData`以及`Lifecycle/LifecycleOwner`几乎快要被新的、和Kotlin所一起推动的`lifecycleScope`、`StateFlow`取代了。
> 由衷感慨迭代之快容易让人应接不暇，不得不多从这些事物中多寻找那些不变的本质，来更好在这潮流里前行。

`LifecycleScope`是`LifecycleOwner`的拓展属性，类型是`LifecycleCoroutineScope`，在`CoroutineScope`上加了个Lifecycle成员。

由于是`CoroutineScope`，其作用不言自明，所有的需要与此生命周期绑定的Kotlin异步任务，都可以在此上下文中展开。

而ViewModel也提供了`ViewModelScope`与ViewModel的生命周期绑定。它们往往提供了一些`launchWhenCreate`, `launchWhenStart`方法，用于在特定生命周期事件后开始异步任务。

使用体感上来说会比`LifecycleObserver`这样的东西好用不少。对`Lifecycle`的生命周期观测，也要逐渐沉入历史的海洋了。至于协程相关知识，请参阅“Kotlin”section.

## DataStore

## Compose

请参阅“跨端技术”section

## android-dagger

请参阅“设计模式”section

## View Binding

### 概述

在我的认知中，它的演进过程是这样的：

远古时期：
```
iv = (ImageView) container.findViewById(R.id.xxxxxx);
```
黄油刀ButterKnife时期（典型的依赖注入特殊场景）：
```
@Bind(R.id.xxxxxx)
ImageView iv;

onCreate(..){xxxx.install(this);}
```
Kotlin初期：
```
val iv by lazy{
    findViewById<ImageView>(R.id.xxxxxx)
}
```
Kotlin稍后时期：
```
import ..kotlin...activity.some_id_of_image_view as someIv
```
现在(layout xml: some_activity.xml)：
```
val binding = SomeActivityBinding.inflate(Inflater,...)
or 
val binding = SomeActivityBinding.bind(View)

使用：

binding.iv
```
若想查看自动生成的xxxBinding文件，需要在gradle module目录下的Build里搜索，好像是viewBinding里。

> 又是一次感叹，关于可能不到一年前，还使用Kotlin所自动为layout xml所生成的id资源，没持续多久，现在又已变成了ViewBinding。

## coil

[coil](https://github.com/coil-kt/coil)是一个基于Kotlin写的图片加载库，它为ImageView添加了拓展方法，从而在获取到ImageView的时候很方便地调用`imageView.load("url")`或者其他重载方法。当然也提供了独立的`coil`封装即作为client来发起请求，获取图片、drawable之类。

欢迎来到新的时代~