# Java并发概述

我们将写一些文章来介绍Java并发上的一些须知须会

- Synchronize 的优化（无锁、偏向锁、轻量锁、重量锁）以及JVM上的实现原理（对象头）
- Atomic原子库（CAS，ABA问题）
- [Volatile关键字](https://juejin.im/post/5a2b53b7f265da432a7b821c)
- 线程安全的集合实现
- Object自身的同步wait, notify等，Thread的join等
- 线程池，多线程同步， CountDownLatch, Barrier

不过在此之前，还是需要了解一些如下的知识才可以更好进行：

- Jvm内存模型


## 线程和进程

线程是cpu最小的调度单位，进程是系统的最小资源分配单位。
注意理解系统的线程和jvm的线程。java里的线程实际上的实现取决于jvm，但一般jvm实现的时候都会将其映射到系统提供的线程能力上。以获得更好的cpu利用。

另外，jvm上除了java我们创建的线程，还会有jvm自己的一些线程。甚至包括debugger


## wait, notify, notifyall

一定要先持有这个对象的锁，然后才可以调用这些方法。也就是一定要在`synchronized`此对象中调用。并且需要等notify的线程退出自己的`synchronized`块。

`wait`会释放掉`synchronized`持有的锁。`synchronized`是可以重入的。

## ReentrantLock, ReentrantReadWriteLock, CountDownLatch
都是基于AQS。AQS是通过int来维护状态的同步器基类，支持两种方式工作（但一般只选择一种，独占模式、共享模式），独占模式是state=0表示空闲，占有的时候一般会+1，共享模式是state>0表示空闲，占有的时候通过-acquire。上述的这些`ReentrantLock`, `ReentrantReadWriteLock`都包含了公平、非公平的实现，每个实现各自都是`aqs`的具体子类。

公平和非公平的实现差异主要在于：
- 非公平，在`tryAcquire`的时候，检查state空闲，就会acquire(即compareAndSetState)。
- 公平，在`tryAcquire`的时候，检查state空闲，如果自己的线程不是在等待队列的head的话，会返回false。如果等待队列为空的话，则也会尝试acquire。或者自己已经是占有线程了直接acquire。

此外，`synchronized`也是可以重入的。
read more(https://juejin.cn/post/6844903997438951437#heading-23)


## 学java并发
http://tutorials.jenkov.com/java-concurrency/thread-signaling.html


## thread local
- thead{threadLocalMap}
- ThreadLocalMap(ThreadLocal -> Object)
- looper(sLooper(mainLooper), sThreadLocal = ThreadLocal()(用于myLooper的返回（即当前线程的looper）->sThreadLocal.get()-> Thread.threadLocalMap.get(sThreadLocal)->object)