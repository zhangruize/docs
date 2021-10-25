## HashMap

### slot, bucket

HashMap中以数组的形式存储了slot（槽）。当哈希碰撞时，会在slot中遍历里面的数据，当小于“树”化的阈值时，以链表存储，大于时会“树”化，采用的是“红黑树”。当小于“去树”化的阈值时又退回到链表。

### 哈希值计算

为了更好的性能，HashMap没有使用求余%或者除法，因为这是最耗时的指令。而是采用了和slot的尺寸进行按位与&操作确定的slot下标（参加Thinking in java）。此外在对key调用hashCode()后还进行了一步处理，把hashCode()返回结果与此结果无符号右移16位进行亦或（^）运算。此原因是因为当map容量较小时，因为上面&的原因，导致对于高bit位差异大，低bit位差异小的hashCode情况会很容易发生碰撞（float为key的案例）。

### 容量 & 负载因子

容量必须是2的指数，原因上面也已经涉及，此外还可以在扩容的时候，一定程度避免了数据移动，负载因子默认0.75，也是平均性能最优的结果。容量与负载因子的乘积，将决定map实际存储量达到多少时扩容。之所以不在达到容量上限才扩容是为了更好的性能。

### 时间复杂度

slot查询O(1)，当然前提是key的hashCode()是O(1)。Bucket查询最差情况（哈希全碰撞）O(n)或O(log n)这取决于是链表还是树，最好情况O(1)，因为完全均匀且就仅一个元素。

### Node

Node是存储HashMap存储元素的节点。但HashMap为了LinkedHashMap保留了newNode方法，以便LinkedHashMap创建特殊的一种Node。详见“LinkedHashMap理解”。

而对于“树”化后，存储的节点会变为TreeNode，为了支持树的结构，增加了更多的字段。

### Null兼容性

支持Null的Key或Value。null的key的HashCode认为是0

### 线程安全性

线程不安全

### 有序性

不保证有序

### 扩容

容量翻倍，对于链表结构，原来链表下的节点会被重排为两个链表，只需要取扩容时新增的那位bit即老的容量（oldCap）与节点的hash 进行与运算即可，分成的两个新链表对应了新的那一位是否为0.

### jdk 7 -> jdk 8
由数组+链表的结构改为数组+链表+红黑树。
优化了高位运算的hash算法：h^(h>>>16)
扩容后，元素要么是在原位置，要么是在原位置再移动2次幂的位置，且链表顺序不变。
最后一条是重点，因为最后一条的变动，hashmap在1.8中，不会在出现死循环问题。

## LinkedHashMap

LinkedHashMap最常用于LRU算法的缓存实现（使用accessOrder模式），另一个模式是按插入顺序。此外它还保证了顺序。

### 与HashMap的关系

继承于HashMap，并重写了newNode方法，它的Node类型也继承了HashMap的Node，主要是为了支持双向链表增加了before和after节点。整体存储方式还是依赖了HashMap，即put和get依赖了HashMap的实现，但对于accessOrder模式时，对get会调整链表的顺序。

### LRU过程实现

当accessOrder=true的时候，LinkedHashMap通过重写onNodeAccess，会把访问的元素从链表取出，并重新放在链表尾端（head, tail都存在成员变量）。列表头是最少被访问的，因此是最少被使用的（但如果，这个对象以其他方式在不断被使用，那么另当别论）。但整个过程LinkedHashMap都不会自动进行删除操作。如果使用者需要增加删除逻辑，可以复写removeEldestEntry(Node node)，这是在put时，会触发这个方法，可以在这里返回布尔值，方便决定是否直接删除最老的节点（头节点）。

## ConcurrentHashMap

- 在jdk1.7使用的是分段锁。
- 后面主要以jdk1.8为准。同步主要使用了cas（当目标下标的tab上还没元素时，使用cas添加新元素，失败则进入下一次处理）和局部的synchronize（当目标下标tab已经有元素时，获取根元素的锁）关键字。在put的时候会加上循环，直到成功为止。
- 核心的理念和hashmap相同。

## TreeMap

### 算法

红黑树

### 时间复杂度

保证O(log n)

### 有序性

会根据构造器的comparator, 或者存储的key实现的comparable接口来排序。有序的。

### 特点

需要使用comparator或者key实现comparable。否则会出错。

### vs HashMap

同上面所述，如果需要按key排序，则此map较为合适，如果不需要，而且key也没有实现这些方法，不提供比较器，则没必要使用treeMap。

### vs LinkedHasMap

同上。虽然两者都有序。但排序方式不同。