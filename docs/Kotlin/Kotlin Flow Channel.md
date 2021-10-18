## Flow

Flow一般意义上代表一组独立的异步数据。“独立”意味着，对同一个Flow类型的不同实例进行collect，都应该各自得到预期的异步数据。Flow在Collect的时候开始产生异步数据。这也被称为“冷流”。但还有一类`SharedFlow`则是非常规的流，被称为“热流”，因为它们永不完成，而且用于以广播形式共享所有发送的值。

## Channel

Channel则代表了更一般的，协程上的异步数据，它的产生和接收都是更加弹性的，且对同一个Channel实例接收，会公平地接收下一个数据。

Channel则包含了SendChannel和ReceiveChannel，代表了数据发送和数据接受的具体行为。Channel中的数据，默认会采用“相遇”策略，发送者发送后等待接受者接收，接受者接收时会等待发送者发送，否则均会阻塞。也可以更改为其他策略，类似可以缓存指定数量的元素，或者一直保留最新的。另外，一般的Channel的接受者，都是按调用接收的顺序，公平地一个一个接收下一个数据，若需要广播形式的接收，则需要`BroadcastChannel`，它实际是一个`SendChannel`。

## StateFlow

`StateFlow: SharedFlow`, `MutableStateFlow:MutableSharedFlow`

它是一个特殊的flow。在android开发更为常用，其特点在于内部维护了一个状态。随时可以读取来获取目前的状态，也可以以`collect`方式订阅状态变化。它类似`LiveData`，尤其当配合`lifecycleScope`一起使用的时候。比如`lifecycleScope.launchWhenStart{ someState.collect{} }`。在collect的时候，都会先收到当前状态。此外多个收集器收集时，都会得到分发，因为它也是`SharedFlow`，因此它的订阅是无止境的，且是广播形式共享的。

## SharedFlow

订阅者以`slot`方式维护，`slot`会在`collect`方法中进入循环，并一般开启一个协程来等待唤起。唤起后即可读取新的emit值。

## 拓展阅读

[Kotlin负责人的Cold flow, Hot channel文章](https://elizarov.medium.com/cold-flows-hot-channels-d74769805f9)
[Flow & Channel理解、对比](https://proandroiddev.com/going-deep-on-flows-channels-part-1-streams-5ae8b8491ac4)