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

ActivityWatcher的：
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

FragmentAndViewModelWatcher:
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

RootViewWatcher:
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