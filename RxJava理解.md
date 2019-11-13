# RxJava

## pull vs push

有这么一种比较小众的说法，但或许有一定道理，“RxJava体现出来的Pull和Push”的区别？这个初听起来无法理解，但可以参考一个[文章](https://www.uwanttolearn.com/android/pull-vs-push-imperative-vs-reactive-reactive-programming-android-rxjava2-hell-part2/)。在这个文章里，首先pull本身是指自己（观测者）去主动请求数据，push则是指（被观测者）数据变更时主动去通知观测者。pull如果想得到新的数据，往往需要poll，即不断去轮询，而push如果想得到新的数据，往往需要callback即可。而RxJava便是这样的工具，可以大量减少Callback这样的样板代码。而对应Android方面，这里更多的场景便是指网络请求或者页面事件等。

## subscribeOn vs observeOn

关于RxJava，想到的一些特点是，链式调用，以及很多清晰、易用的基本原语、术语、操作符。onNext, onError, onComplete, Schedulers, Single, Flowable, map, flatMap等等。好用的同时，有时候会引入令人混淆的东西，比如observeOn以及subscribeOn，以及两者多次穿插会发生什么。

subscribeOn，即在哪里订阅，订阅完就该产生数据了，指的是被观测者在onSubscribe时候触发的线程环境。它的本质是生成一个新的被观测者，新生成的被观测者在onSubscribe即被订阅的时候，使用Schedulers里安排的线程上，触发自己本身的原始onSubscribe内容。

observeOn，即在哪里观测，指的是观测者被回调时期望的线程环境。它的本质是生成一个新的被观测者，新生成的被观测者在onSubscribe即被订阅的时候，会订阅原被观测者，此时的观测者是自身（即新生成的被观测者），在新生成的被观测者onNext,onError, onComplete时候，会使用Schedulers提供的线程，切换环境后调用后面观测者的onNext, onError, onComplete等。

思考下复杂场景，X.subscribeOn(A).observeOn(B).subscribe(O)。注意最后.subscribe(O)把观测者O传递给了上面倒数第二步生成的被观测者。而这个观测者根据上面所述，会进而继续调用前面的被观测者，即observeOn所生成的。而observeOn所生成的被观测者会准备在A线程环境中进而调用X从而产生数据。在产生完数据后，会回到subscribeOn所生成的被观测者，此时它的观测者是自己，在onNext又切换了线程最后在线程B中把数据交付给了O。

X.subscribeOn(A).observeOn(B).subscribeOn(C).subscribe(O)
可以试着分析一下，X应该会在A线程被调用，O应该会在B中调用

一个比较好的解释此方面的[文章](https://blog.csdn.net/michael1112/article/details/78688099)

## 链式调用

关于RxJava的链式调用，也远比看上去的更有意思。这里的链式调用虽然方法按顺序不断从上到下进行调用，但实际上每个方法都只是在进行包装，而最终只有subscribeXxx才会触发完整的封装链条调用。而且根据封装内容，最终封装后的产物在调用时是有一种从下到上的感觉。
