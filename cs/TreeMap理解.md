# TreeMap理解

## 算法

红黑树

## 时间复杂度

保证O(log n)

## 有序性

会根据构造器的comparator, 或者存储的key实现的comparable接口来排序。有序的。

## 特点

需要使用comparator或者key实现comparable。否则会出错。

## vs HashMap

同上面所述，如果需要按key排序，则此map较为合适，如果不需要，而且key也没有实现这些方法，不提供比较器，则没必要使用treeMap。

## vs LinkedHasMap

同上。虽然两者都有序。但排序方式不同。