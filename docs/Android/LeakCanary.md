是一个安卓上用来检测内存泄漏的库。易于使用和内部诸多实现原理对性能领域都是值得研究借鉴的。

## 启动方式

只需要gradle引入`debugImplementation`依赖即可。实际是`ContentProvider`方式来提供了初始化。清单定义：
```xml
<provider
    android:name="leakcanary.internal.AppWatcherInstaller$MainProcess"
    android:authorities="${applicationId}.leakcanary-installer"
    android:enabled="@bool/leak_canary_watcher_auto_install"
    android:exported="false"/>
```
`leakcanary.internal.AppWatcherInstaller$MainProcess`的具体定义只需要关注：
```java
  override fun onCreate(): Boolean {
    val application = context!!.applicationContext as Application
    AppWatcher.manualInstall(application)
    return true
  }
```
具体的`install`过程是指：
```java
      ActivityWatcher(application, reachabilityWatcher),
      FragmentAndViewModelWatcher(application, reachabilityWatcher),
      RootViewWatcher(reachabilityWatcher),
      ServiceWatcher(reachabilityWatcher)
```
这4个默认的watcher的install调用。分开来看：

## ActivityWatcher
```kotlin
class ActivityWatcher(...
) : InstallableWatcher {

  private val lifecycleCallbacks =
    object : Application.ActivityLifecycleCallbacks by noOpDelegate() {
      override fun onActivityDestroyed(activity: Activity) {
          //核心，即在Activity销毁回调时，触发一次预期弱引用检查
        reachabilityWatcher.expectWeaklyReachable(
          activity, "${activity::class.java.name} received Activity#onDestroy() callback"
        )
      }
    }

  override fun install() {
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
  }

  override fun uninstall() {
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
  }
}
```

## FragmentAndViewModelWatcher
```java
class FragmentAndViewModelWatcher(
  private val application: Application,
  private val reachabilityWatcher: ReachabilityWatcher
) : InstallableWatcher {
  private val lifecycleCallbacks =
    object : Application.ActivityLifecycleCallbacks by noOpDelegate() {
      override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
      ) {
        for (watcher in fragmentDestroyWatchers) {
          watcher(activity)
        }
      }
    }

  override fun install() {
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
  }

  override fun uninstall() {
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
  }
}
```
其中`fragmentDestroyWatchers`具体是指`AndroidXFragmentDestroyWatcher`、`AndroidSupportFragmentDestroyWatcher` 其中之一。代码都很相似，查看一个即可：
```java
internal class AndroidXFragmentDestroyWatcher(
  private val reachabilityWatcher: ReachabilityWatcher
) : (Activity) -> Unit {

  private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentCreated(
      fm: FragmentManager,
      fragment: Fragment,
      savedInstanceState: Bundle?
    ) {
      ViewModelClearedWatcher.install(fragment, reachabilityWatcher)
    }

    override fun onFragmentViewDestroyed(
      fm: FragmentManager,
      fragment: Fragment
    ) {
      val view = fragment.view
      if (view != null) {
        reachabilityWatcher.expectWeaklyReachable(
          view, "${fragment::class.java.name} received Fragment#onDestroyView() callback " +
          "(references to its views should be cleared to prevent leaks)"
        )
      }
    }

    override fun onFragmentDestroyed(
      fm: FragmentManager,
      fragment: Fragment
    ) {
      reachabilityWatcher.expectWeaklyReachable(
        fragment, "${fragment::class.java.name} received Fragment#onDestroy() callback"
      )
    }
  }

  override fun invoke(activity: Activity) {
    if (activity is FragmentActivity) {
      val supportFragmentManager = activity.supportFragmentManager
      supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true)
      ViewModelClearedWatcher.install(activity, reachabilityWatcher)
    }
  }
}
```
即使用`FragmentManager.registerFragmentLifecycleCallbacks`来注册生命周期回调，在`view`销毁时检查`view`，在`fragment`销毁时，检查`fragment`。


## ViewModelClearedWatcher

它依赖于`AndroidX`下的`ViewModelStoreOwner`，具体子类是`FragmentActivity`和`Fragment`，它通过`ViewModelStoreOwner`构造了`ViewModelProvider`并创建了自己的`ViewModel`作为监视器，该`ViewModel.onCleared`回调中，会反射获取`ViewModelStoreOwner.viewModelStore`的私有成员`mMap`进而得到所有的`ViewModel`，并检测它们是否处于弱访问状态。

## RootViewWatcher
```java
class RootViewWatcher(
  private val reachabilityWatcher: ReachabilityWatcher
) : InstallableWatcher {

  private val listener = OnRootViewAddedListener { rootView ->
    val trackDetached = when(rootView.windowType) {
      PHONE_WINDOW -> {
        when (rootView.phoneWindow?.callback?.wrappedCallback) {
          // Activities are already tracked by ActivityWatcher
          is Activity -> false
          is Dialog -> {
            // Use app context resources to avoid NotFoundException
            // https://github.com/square/leakcanary/issues/2137
            val resources = rootView.context.applicationContext.resources
            resources.getBoolean(R.bool.leak_canary_watcher_watch_dismissed_dialogs)
          }
          // Probably a DreamService
          else -> true
        }
      }
      // Android widgets keep detached popup window instances around.
      POPUP_WINDOW -> false
      TOOLTIP, TOAST, UNKNOWN -> true
    }
    if (trackDetached) {
      rootView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {

        val watchDetachedView = Runnable {
          reachabilityWatcher.expectWeaklyReachable(
            rootView, "${rootView::class.java.name} received View#onDetachedFromWindow() callback"
          )
        }

        override fun onViewAttachedToWindow(v: View) {
          mainHandler.removeCallbacks(watchDetachedView)
        }

        override fun onViewDetachedFromWindow(v: View) {
          mainHandler.post(watchDetachedView)
        }
      })
    }
  }

  override fun install() {
    Curtains.onRootViewsChangedListeners += listener
  }

  override fun uninstall() {
    Curtains.onRootViewsChangedListeners -= listener
  }
}
```
这里在附加过程中，使用了`Curtains`，这个工具做了一个比较trick的东西，它通过反射获取了`WindowManagerGlobal`并把`mViewList:List<RootViewImpl>`这个成员给替换成了自己的List代理，内部元素依然没有改变，只是增加的这一层可以获得`add`, `remove`的切面由此可以发送相应的监听事件。这里监听者即是`RootViewWatcher`，而它对`ViewRootImpl`所属的Window的类型做了一些检查。去跟了下代码实现，认为不是很值得了解。这个`watcher`也就作罢。

## ObjectWatcher

它也是扮演真正检查引用是否被合理释放的角色。
```java
  @Synchronized override fun expectWeaklyReachable(
    watchedObject: Any,
    description: String
  ) {
    if (!isEnabled()) {
      return
    }
    //理解为pruneList，即触发一波列表净化
    removeWeaklyReachableObjects()
    val key = UUID.randomUUID()
    val watchUptimeMillis = clock.uptimeMillis()
    //KeyedWeakRefence是WeakReference子类，增加了几个成员变量key, description, time
    val reference =
      KeyedWeakReference(watchedObject, key, description, watchUptimeMillis, queue)
    //注册进入观察列表。
    watchedObjects[key] = reference
    checkRetainedExecutor.execute {
      //启动一次检查
      moveToRetained(key)
    }
  }

  //净化列表，即ReferenceQueue.poll一波连续非空元素。对每个元素从观察列表移除。因为处于ReferenceQueue时说明其已经会被GC了，尽管尚未GC。
  private fun removeWeaklyReachableObjects() {
    var ref: KeyedWeakReference?
    do {
      ref = queue.poll() as KeyedWeakReference?
      if (ref != null) {
        watchedObjects.remove(ref.key)
      }
    } while (ref != null)
  }

    @Synchronized private fun moveToRetained(key: String) {
      //触发净化列表
    removeWeaklyReachableObjects()
    val retainedRef = watchedObjects[key]
    if (retainedRef != null) {
      //如果还处在观察列表，记录驻留检测时间。
      retainedRef.retainedUptimeMillis = clock.uptimeMillis()
      //触发驻留事件回调
      onObjectRetainedListeners.forEach { it.onObjectRetained() }
    }
  }
```
在LeakCanary中对象驻留回调的实现主要如下：
```java
  fun scheduleRetainedObjectCheck() {
    if (this::heapDumpTrigger.isInitialized) {
      //调度对象驻留检测，其内部会先检测若已经启动，则忽略，若没有启动过，则在后台线程调度一次任务
      heapDumpTrigger.scheduleRetainedObjectCheck()
    }
  }
  //对象驻留检测的核心逻辑
    private fun checkRetainedObjects() {
    val iCanHasHeap = HeapDumpControl.iCanHasHeap()
    val config = configProvider()
    if (iCanHasHeap is Nope) {
      ... //不能heap先不看
      return
    }
    var retainedReferenceCount = objectWatcher.retainedObjectCount

    if (retainedReferenceCount > 0) {
      gcTrigger.runGc()
      retainedReferenceCount = objectWatcher.retainedObjectCount
    }

    if (checkRetainedCount(retainedReferenceCount, config.retainedVisibleThreshold)) return

    val now = SystemClock.uptimeMillis()
    val elapsedSinceLastDumpMillis = now - lastHeapDumpUptimeMillis
    if (elapsedSinceLastDumpMillis < WAIT_BETWEEN_HEAP_DUMPS_MILLIS) {
      ...//重新延迟调度
      return
    }
    dumpHeap(
      retainedReferenceCount = retainedReferenceCount,
      retry = true,
      reason = "$retainedReferenceCount retained objects, app is $visibility"
    )
  }
```

## GC触发

要点是`Runtime.getRuntime().gc()`而非`System.gc()`，前者更容易触发GC。
```java
  object Default : GcTrigger {
    override fun runGc() {
      // Code taken from AOSP FinalizationTest:
      // https://android.googlesource.com/platform/libcore/+/master/support/src/test/java/libcore/
      // java/lang/ref/FinalizationTester.java
      // System.gc() does not garbage collect every time. Runtime.gc() is
      // more likely to perform a gc.
      Runtime.getRuntime()
        .gc()
      enqueueReferences()
      System.runFinalization()
    }

    private fun enqueueReferences() {
      // Hack. We don't have a programmatic way to wait for the reference queue daemon to move
      // references to the appropriate queues.
      try {
        Thread.sleep(100)
      } catch (e: InterruptedException) {
        throw AssertionError()
      }
    }
  }
```

## DumpHeap

核心是使用`Debug`类：
```java
Debug.dumpHprofData(heapDumpFile.absolutePath)
```
> Debug类中有不少有趣的工具，包括等待调试`waitForDebugger`等。

## HeapAnalyzer

### .hprof

hprof是java提供的工具，用于进行cpu/heap的剖析。其格式定义见[这里](https://www.cs.rice.edu/~la5/doc/hprof.html/d1/d3f/hprof__b__spec_8h_source.html)，或者[这里](http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html)

### 更多方式

Dump heap方式不止一种，有更多的工具可以处理，但其内部工作机理应该均基于JVM TI(Tool Interface)，它提供了大量的JVM 内部状态的访问功能，涵盖了线程、栈帧、堆、局部变量、断电、观察字段等等，估计InteliJ的调试能力没少依赖这个。

## PlumberAndroid

这个包名很有趣：Plumber即水管工，它是用于提供一系列默认的安卓框架下的内存泄漏修复。依然使用ContentProvider的方式注入。依然轻量级。最trick和hack的地方，已经被它所收纳了。核心类：`AndroidLeakFixes`

## 拓展阅读

- [hprof格式说明1](https://www.cs.rice.edu/~la5/doc/hprof.html/d1/d3f/hprof__b__spec_8h_source.html)
- [hprof格式说明2](http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html)
- [Oracle hprof老介绍](https://docs.oracle.com/javase/7/docs/technotes/samples/hprof.html)
- [Oracle JVM TI](https://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html)