# ConcurrentHashMap理解

- 在jdk1.7使用的是分段锁。
- 后面主要以jdk1.8为准。同步主要使用了cas（当目标下标的tab上还没元素时，使用cas添加新元素，失败则进入下一次处理）和局部的synchronize（当目标下标tab已经有元素时，获取根元素的锁）关键字。在put的时候会加上循环，直到成功为止。
- 核心的理念和hashmap相同。