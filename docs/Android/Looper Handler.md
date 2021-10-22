## Looper

### 概述

Looper结合内部的MessageQueue会维护一个线程内单例的消息队列，它借助了`epoll`系统调用来等待兴趣fd列表的事件。这份兴趣Fd列表不仅包含用于其他线程唤醒自己的fd，还可以被添加新的关注fd或者移除。这对于关注vsync, inputEvent的fd都提供了方式。此外，它的消息队列在native侧和java侧是独立的，进而可以互不干扰地对两侧的消息进行分发。对于java侧的消息还有一则限制，即必须有handler作为message的target（仅有下文提到的唯一特殊情况例外）。

### 实现细节

Looper构造，仅可通过`Looper.prepare()`构造，构造方法中会创建新的`MessageQueue`。即`MessageQueue`和`Looper`一对一。

Looper.prepare，用于为线程设定Looper单例。Looper是线程内单例。cpp的代码主要是` pthread_setspecific(gTLSKey, looper.get());  `，java侧则是`  sThreadLocal.set(new Looper(quitAllowed));  `

`Looper.loop`，一个死循环，直到有退出消息。从`MessageQueue#next`获取信息，阻塞当前线程。
```java
// Looper.java
loop(){....
    for (;;) {
        if (!loopOnce(me, ident, thresholdOverride)) {
            return;
        }
    }
}
// 精简过的LoopOnce
private static boolean loopOnce(final Looper me,
        final long ident, final int thresholdOverride) {
    Message msg = me.mQueue.next(); // might block
    if (msg == null) {
        // No message indicates that the message queue is quitting.
        return false;
    }

    // This must be in a local variable, in case a UI event sets the logger
    final Printer logging = me.mLogging;
    if (logging != null) {
        logging.println(">>>>> Dispatching to " + msg.target + " "
                + msg.callback + ": " + msg.what);
    }
    // Make sure the observer won't change while processing a transaction.
    final Observer observer = sObserver;
    try {
        msg.target.dispatchMessage(msg);
        if (observer != null) {
            observer.messageDispatched(token, msg);
        }
    } catch (Exception exception) {
        if (observer != null) {
            observer.dispatchingThrewException(token, msg, exception);
        }
        throw exception;
    }
    if (logging != null) {
        logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
    }
    msg.recycleUnchecked();
    return true;
}
```
而`MessageQueue.next`实现如下：
```java
// MessageQueue.java 已精简
Message next() {
    // Return here if the message loop has already quit and been disposed.
    // This can happen if the application tries to restart a looper after quit
    // which is not supported.
    final long ptr = mPtr;
    if (ptr == 0) {
        return null;
    }

    for (;;) {
        nativePollOnce(ptr, nextPollTimeoutMillis);
        synchronized (this) {
            // Try to retrieve the next message.  Return if found.
            final long now = SystemClock.uptimeMillis();
            Message prevMsg = null;
            Message msg = mMessages;
            if (msg != null && msg.target == null) {
                // Stalled by a barrier.  Find the next asynchronous message in the queue.
                do {
                    prevMsg = msg;
                    msg = msg.next;
                } while (msg != null && !msg.isAsynchronous());
            }
            if (msg != null) {
                if (now < msg.when) {
                    // Next message is not ready.  Set a timeout to wake up when it is ready.
                    nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                } else {
                    // Got a message.
                    mBlocked = false;
                    if (prevMsg != null) {
                        prevMsg.next = msg.next;
                    } else {
                        mMessages = msg.next;
                    }
                    msg.next = null;
                    if (DEBUG) Log.v(TAG, "Returning message: " + msg);
                    msg.markInUse();
                    return msg;
                }
            } else {
                // No more messages.
                nextPollTimeoutMillis = -1;
            }

            // Process the quit message now that all pending messages have been handled.
            if (mQuitting) {
                dispose();
                return null;
            }

            // If first time idle, then get the number of idlers to run.
            // Idle handles only run if the queue is empty or if the first message
            // in the queue (possibly a barrier) is due to be handled in the future.
            if (pendingIdleHandlerCount < 0
                    && (mMessages == null || now < mMessages.when)) {
                pendingIdleHandlerCount = mIdleHandlers.size();
            }
            if (pendingIdleHandlerCount <= 0) {
                // No idle handlers to run.  Loop and wait some more.
                mBlocked = true;
                continue;
            }

            if (mPendingIdleHandlers == null) {
                mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
            }
            mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
        }

        // Run the idle handlers.
        // We only ever reach this code block during the first iteration.
        for (int i = 0; i < pendingIdleHandlerCount; i++) {
            final IdleHandler idler = mPendingIdleHandlers[i];
            mPendingIdleHandlers[i] = null; // release the reference to the handler
            boolean keep = false;
            try {
                keep = idler.queueIdle();
            }

            if (!keep) {
                synchronized (this) {
                    mIdleHandlers.remove(idler);
                }
            }
        }
    }
}
```
`nativePollOnce`本质是调用了`Looper#pollOnce`->`pollInner`。
```cpp
// Looper.cpp 已精简
int Looper::pollInner(int timeoutMillis) {
    mResponses.clear();
    mResponseIndex = 0;
    struct epoll_event eventItems[EPOLL_MAX_EVENTS];
    int eventCount = epoll_wait(mEpollFd.get(), eventItems, EPOLL_MAX_EVENTS, timeoutMillis);
    mPolling = false;
    for (int i = 0; i < eventCount; i++) {
        const SequenceNumber seq = eventItems[i].data.u64;
        uint32_t epollEvents = eventItems[i].events;
        if (seq == WAKE_EVENT_FD_SEQ) {
            if (epollEvents & EPOLLIN) {
                awoken();
            } else {
                ALOGW("Ignoring unexpected epoll events 0x%x on wake event fd.", epollEvents);
            }
        } else {
            const auto& request_it = mRequests.find(seq);
            if (request_it != mRequests.end()) {
                const auto& request = request_it->second;
                int events = 0;
                if (epollEvents & EPOLLIN) events |= EVENT_INPUT;
                if (epollEvents & EPOLLOUT) events |= EVENT_OUTPUT;
                if (epollEvents & EPOLLERR) events |= EVENT_ERROR;
                if (epollEvents & EPOLLHUP) events |= EVENT_HANGUP;
                mResponses.push({.seq = seq, .events = events, .request = request});
            } else {
                ALOGW("Ignoring unexpected epoll events 0x%x for sequence number %" PRIu64
                      " that is no longer registered.",
                      epollEvents, seq);
            }
        }
    }
    // Invoke pending message callbacks.
    mNextMessageUptime = LLONG_MAX;
    while (mMessageEnvelopes.size() != 0) {
        nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
        const MessageEnvelope& messageEnvelope = mMessageEnvelopes.itemAt(0);
        if (messageEnvelope.uptime <= now) {
            // Remove the envelope from the list.
            // We keep a strong reference to the handler until the call to handleMessage
            // finishes.  Then we drop it so that the handler can be deleted *before*
            // we reacquire our lock.
            { // obtain handler
                sp<MessageHandler> handler = messageEnvelope.handler;
                Message message = messageEnvelope.message;
                mMessageEnvelopes.removeAt(0);
                mSendingMessage = true;
                mLock.unlock();
                handler->handleMessage(message);
            } // release handler
            mSendingMessage = false;
            result = POLL_CALLBACK;
        }
    }
    // Invoke all response callbacks.
    for (size_t i = 0; i < mResponses.size(); i++) {
        Response& response = mResponses.editItemAt(i);
        if (response.request.ident == POLL_CALLBACK) {
            int fd = response.request.fd;
            int events = response.events;
            void* data = response.request.data;
#if DEBUG_POLL_AND_WAKE || DEBUG_CALLBACKS
            ALOGD("%p ~ pollOnce - invoking fd event callback %p: fd=%d, events=0x%x, data=%p",
                    this, response.request.callback.get(), fd, events, data);
#endif
            // Invoke the callback.  Note that the file descriptor may be closed by
            // the callback (and potentially even reused) before the function returns so
            // we need to be a little careful when removing the file descriptor afterwards.
            int callbackResult = response.request.callback->handleEvent(fd, events, data);
            // Clear the callback reference in the response structure promptly because we
            // will not clear the response vector itself until the next poll.
            response.request.callback.clear();
            result = POLL_CALLBACK;
        }
    }
    return result;
}
```

即本质是 `  int eventCount = epoll_wait(mEpollFd.get(), eventItems, EPOLL_MAX_EVENTS, timeoutMillis);  `，即epoll系统调用，可参阅“Linux”section。`eventItems`局部变量会接收到事件列表。而收到事件之后，这里的处理主要分三种情况：

- 如果是`wake`事件，确认无误后，单纯唤醒。
- 如果有`mMessageEnvelopes`处理，则处理`message`，特别注意，这里的`message`是通过`MessageQueue.cpp`的`postMessage`或者调用了`Looper.cpp`的`sendMessage`系列方法入队的信息。对于java侧的信息，并不会在这个vector之中（这一点让我当时困惑半天）。相当于native侧的`handler.postMessage()`。
- 如果有`mResponses`处理，则处理这些回调。相当于native侧的`postRunnable`。

而对于java侧的`MessageQueue.enqueueMessage`方法，它的实现如下：
```java
    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {
            throw new IllegalArgumentException("Message must have a target.");
        }

        synchronized (this) {
            if (msg.isInUse()) {
                throw new IllegalStateException(msg + " This message is already in use.");
            }

            if (mQuitting) {
                IllegalStateException e = new IllegalStateException(
                        msg.target + " sending message to a Handler on a dead thread");
                Log.w(TAG, e.getMessage(), e);
                msg.recycle();
                return false;
            }

            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                // New head, wake up the event queue if blocked.
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                // Inserted within the middle of the queue.  Usually we don't have to wake
                // up the event queue unless there is a barrier at the head of the queue
                // and the message is the earliest asynchronous message in the queue.
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p; // invariant: p == prev.next
                prev.next = msg;
            }

            // We can assume mPtr != 0 because mQuitting is false.
            if (needWake) {
                nativeWake(mPtr);
            }
        }
        return true;
    }
```
即本质上压根不涉及native侧的东西，只有最后的`nativeWake`。而`nativeWake`最终会调用到`Looper.cpp`的`wake`方法：
```cpp
void Looper::wake() {
    uint64_t inc = 1;
    ssize_t nWrite = TEMP_FAILURE_RETRY(write(mWakeEventFd.get(), &inc, sizeof(uint64_t)));
    ...
}
```
而`mWakeEventFd`毫无疑问是`epoll_wait`感兴趣的fd之一，由此结合前面`MessageQueue.java`的`next`方法，可以看到，`mMessages`链表表头是直接在`java`侧完成的多线程修改，仅仅只是借助native侧在`wake`的fd上写入下wake事件而已。


## Handler

### 概述

这里主要概述下Handler和Looper的关系。一个Looper可以对应多个Handler。Handler构造时需要传入Looper，以明确Handler的工作MessageQueue。后续使用Handler时，即可完成从`Handler.send/postMessage`的诸多签名方法调用发起线程，发送消息到此`Handler`关联的Looper所在的线程。此消息的target依然是自身`handler`实例，即此`handler`实例从线程A调用了`send/post`等方法后在其对应Looper线程中得到回调执行。

Handler可以在任意线程发起信息、Callback到其定义时所声明的所处线程处理消息、执行回调。

### Async 与 Sync区别

Handler有async和sync两种方式，在创建时指定。这个属性最终会反映在此handler所发送的`Message`实例的`flag`上用于标识`Message`的async和sync区分。

async 和 sync的message具体作用依赖于`MesssageQueue#PostSyncBarrier`。这个方法实际上是插入一个message，target为空（message.target为空也就这一种情况）。在`MessageQueue#next`方法里，如果发现了target=null的message，则会认为是遇到了“同步栅栏”即syncBarrier，会遍历链表找到async为true的message来处理。而不再处理同步消息。直到MessageQueue#removeSyncBarrier发生。

![handler message async](../pics/handler%20message%20async.png)

## Message

### 概述

这里主要想提一下`Message.obtain`，Message类维护了静态的链表作为复用池，池子尺寸上限50个。

## 回答曾经的一个疑问

一开始我以为Handler/Looper出现仅是为了解决线程之间通信。但考虑到有类似`BlockingQueue`这种jdk的线程集合，为何不能用此来通信呢？

回答也很容易，即文章开头内容就可以算作回答。Looper的机制涵盖了java和native两侧的线程间通信。对于不包含jvm的进程下，依然可以使用此机制。以及fd的存在，可以方便用于方便捕获更多IO事件。另外，两者异步的原理也并不相同(epoll vs 线程状态链表+park/unpark)。这里的优劣我暂时无法得出评判。`BlockingQueue`的介绍请参阅“Java”section。

## 拓展阅读

- Looper MessageQueue的源码们。
- [async sync的message差异](https://www.cnblogs.com/angeldevil/p/3340644.html)
