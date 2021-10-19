
## handler message async

async 和 sync的message只有这个boolean的差异。其具体作用依赖于MesssageQueue#PostSyncBarrier。这个方法实际上是插入一个message，target为空（message.target为空也就这一种情况）。在MessageQueue#next方法里，如果发现了target=null的message，则会认为是遇到了“同步栅栏”即syncBarrier，会遍历链表找到async为true的message来处理。而不再处理同步消息。直到MessageQueue#removeSyncBarrier发生。


![handler message async](pics/handler%20message%20async.png)
https://www.cnblogs.com/angeldevil/p/3340644.html
