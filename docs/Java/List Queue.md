## BlockingQueue

BlockingQueue基于Queue接口，但提供了等待队列操作的功能。它的操作方法容易混淆，总结如下：

获取队列头部

- E take() 会出列，会阻塞
- E poll(long timeout, TimeUnit unit) 会出列，设置等待超时
- E element() 不出列，若无元素则异常
- E peek() 不出列，无元素返回空

添加

- boolean add(E)，成功返回true，失败抛异常
- boolean offer(E)，成功返回true，空间不够返回false
- void put(E) 阻塞直到插入
- void offer(E, long timeout, TimeUnit unit) 设置等待超时

它有多种实现，也伴随不同特性。分别简单看下。

### ArrayBlockingQueue

这是最常见的一个实现。内部使用`ReentrantLock`作为锁，以及两个`Condition`分别是`notEmpty`, `notFull`。来看一个阻塞的实现：

```java
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }
    public boolean offer(E e, long timeout, TimeUnit unit)
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0L)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }
```
即如果没有元素且设置了等待超时，则由`notEmpty`Condition来阻塞等待。Condition的工作原理见“Java”section.


## CopyOnWriteArrayList

是一个写入时拷贝的列表，可以理解为它的实例里的数组值是不可变的。每次写入的列表操作都会在临界区里，先得出新的数组，并以此作为内部持有的数组，以确保数组内容的不变性。比如如下的写入操作：

```java
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();
        synchronized (lock) {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException(outOfBounds(index, len));
            if (cs.length == 0)
                return false;
            int numMoved = len - index;
            Object[] newElements;
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + cs.length);
            else {
                newElements = new Object[len + cs.length];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index,
                                 newElements, index + cs.length,
                                 numMoved);
            }
            System.arraycopy(cs, 0, newElements, index, cs.length);
            setArray(newElements);
            return true;
        }
    }
```