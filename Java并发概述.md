# Java并发概述

我们将写一些文章来介绍Java并发上的一些须知须会

- Synchronize 的优化（无锁、偏向锁、轻量锁、重量锁）以及JVM上的实现原理（对象头）
- Atomic原子库（CAS，ABA问题）
- Volatile关键字
- 线程安全的集合实现
- Object自身的同步wait, notify等，Thread的join等
- 线程池，多线程同步， CountDownLatch, Barrier

不过在此之前，还是需要了解一些如下的知识才可以更好进行：

- Jvm内存模型