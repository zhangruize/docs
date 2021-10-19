
首先，不使用这些框架，一般会遇到难以维护、难以拓展、难以测试的问题

![mvvm](http://www.digigene.com/wp-content/uploads/2018/05/compare-MVVMMVPMVC-1024x814.jpeg)

## MVC

View提供基本功能接口，UI事件由View感知，调用controller响应并由controller找Model获取数据。model获取数据之后告知controller（可能不是通过回调而是接口），controller以view接口来停止loading，并告知view。**view再自行从model中读数据结果**。（所以这设计很脑残，大部分场景不合适，3个角色关系有点复杂）

## MVP

View提供基本接口，UI事件由View感知，调用presenter响应并由presenter找model获取数据，数据获取通过回调，而非presenter接口。之后再通过View接口完成UI更新和操作。在这个过程中，**View不需要感知model，不需要操作model。但是presenter和View还存在双向的关系**。

## MVVM

在presenter的基础上，view和ViewModel不再双向关联。ViewModel只需要提供数据订阅。而View根据ui事件自行通过viewModel接口调用去请求新数据，而数据的更新一般通过ViewModel提供的数据订阅完成。

这后面两种的可测试性都要好很多。
实际的View一般是activity或者Fragment，也可以是ViewGroup自定义。