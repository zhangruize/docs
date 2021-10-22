## 应用启动

### 新进程创建前

当用户点击一个应用图标，或者系统请求某个组件（泛指Activity，Service等）时，将是应用启动、Activity启动的一般时机。

1. 构造`Intent`通过`PackageManager`来解析，其内部是用`resolveIntent`，若解析成功，则会保存解析结果到此`Intent`实例以避免后面再次解析。
2. 检查权限，`grantUriPermissionLocked`。对于需要申请权限的`Intent`，需要确定目标组件拥有合适的权限。
3. 接下来，由`ActivityManagerService`检查`Intent`的`flags`比如`FLAG_ACTIVITY_NEW_TASK`、`FLAG_ACTIVITY_CLEAR_TOP`来确定是否需要开启新的任务栈。
4. 接下来检查，对于所需要的进程其`ProcessRecord`是否已经存在，若没有则需要为此组件创建进程。否则只需要进行任务栈的维护，不需要创建进程。

### 新进程创建

 `ActivityManagerService`通过`startProcessLocked`方法，给`Zygote`进程以`Socket`通信发送启动参数。`Zygote`进程使用`fork`系统调用分离出子进程并且调用`ZygoteInit.main()`主方法。并实例化`ActivityThread`，返回进程id结果。

```java
// ProcessList.java，注意这个entryPoint很重要，我找了半天，现在没有显式引用ActivityThread，而是指定className去反射找main方法
    final String entryPoint = "android.app.ActivityThread"; 

    return startProcessLocked(hostingRecord, entryPoint, app, uid, gids,
            runtimeFlags, zygotePolicyFlags, mountExternal, seInfo, requiredAbi,
            instructionSet, invokeWith, startTime); 
```

新的应用进程进入`ActivityThread.main`，初始化了Java侧的handler即`H`类实例，Looper便开始loop，它所对应的线程即为UI线程、主线程。由此后续的通信，便可以基于此Looper和java侧的`H`handler实例。`ActivityThread`的内部类`ApplicationThread`实现了`IApplicationThread`这是`AMS`服务用于调用应用进程进行通信的一组接口，其中包括`bindApplication`和`scheduleTransaction`
```java
// ActivityThread.java 已精简
 public static void main(String[] args) {
        AndroidOs.install();
        Environment.initForCurrentUser();
        TrustedCertificateStore.setDefaultUserDirectory(configDir);
        initializeMainlineModules();

        // Looper准备，绑定线程内Looper单例
        Looper.prepareMainLooper(); 

        // 创建ActivityThread实例，因为我们目前只是在main静态入口方法里。
        ActivityThread thread = new ActivityThread();

        // 在这里会用`ActivityManagerService`的`attachApplication`把`IApplicationThread`的实现实例`appThread`传递过去。由此AMS可以通过`IApplicationThread`和应用进程进行回调。比如后面的`ApplicationThread.bindApplication`
        thread.attach(false, startSeq);
        if (sMainThreadHandler == null) {
            //构造Java侧handler，即`H`类实例
            sMainThreadHandler = thread.getHandler();
        }
        //进入epoll
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }
```
### Application创建

接下来会由AMS调用`IApplicationThread.bindApplication`，其内部用`H`实例发送消息`BIND_APPLICATION`到UI线程，进而到`handleBindApplication`处理。比较关注的过程就是`makeApplication`和`Application.onCreate`

```java 
// ActivityThread.java # handleBindApplication 大幅精简
    final ContextImpl appContext = ContextImpl.createAppContext(this, data.info);
    mInstrumentation = new Instrumentation();
    mInstrumentation.basicInit(this);
    Application app;
    app = data.info.makeApplication(data.restrictedBackupMode, null);
    if (!data.restrictedBackupMode) {
        if (!ArrayUtils.isEmpty(data.providers)) {
            installContentProviders(app, data.providers);
        }
    }
    mInstrumentation.onCreate(data.instrumentationArgs);
    mInstrumentation.callApplicationOnCreate(app);
```
主要的点不言自明。由此可以注意的是：

- 无论是什么组件，但凡需要创建新的进程时，均会经历此初始化，即`application`的创建。
- ContentProvider的创建时早于`Application#onCreate`的，而其他组件则不是。

### Activity启动

当启动Activity的时候，会由`AMS`调用`IApplicationThread`的`scheduleTransaction`，进而由`ApplicationThread`给`H`发送一个`EXECUTE_TRANSACTION`信息，进而交给`TransactionExecutor`去处理事务，进而会分发给不同的`LifecycleItem`来处理事务。

```java
// H
case EXECUTE_TRANSACTION:
    final ClientTransaction transaction = (ClientTransaction) msg.obj;
    mTransactionExecutor.execute(transaction);
    break;
```
进而
```java
// TransactionExecutor.java 已精简
public void executeCallbacks(ClientTransaction transaction) {
    final List<ClientTransactionItem> callbacks = transaction.getCallbacks();
    final IBinder token = transaction.getActivityToken();
    ActivityClientRecord r = mTransactionHandler.getActivityClient(token);
    final int size = callbacks.size();
    for (int i = 0; i < size; ++i) {
        final ClientTransactionItem item = callbacks.get(i);
        item.execute(mTransactionHandler, token, mPendingActions);
        item.postExecute(mTransactionHandler, token, mPendingActions);
    }
}
```
也就是说，`ClientTransactionItem`是由服务端传入的。它的继承关系包含：
ClientTransactionItem 子类

- ActivityTransactionItem
    - ActivityConfigurationChangeItem 
    - ActivityLifecycleItem 
        -  DestroyActivityItem
        -  PauseActivityItem
        -  ResumeActivityItem
        -  StartActivityItem
        -  StopActivityItem
    - ActivityRelaunchItem
    - ActivityResultItem
    - NewIntentItem
    - LaunchActivityItem

每个item的`execute`方法是其具体实现。对于`Activity`来说，先被`Launch`。
```java
// 大幅精简
    public Activity handleLaunchActivity(ActivityClientRecord r,
            PendingTransactionActions pendingActions, Intent customIntent) {
        if (ThreadedRenderer.sRendererEnabled
                && (r.activityInfo.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
            HardwareRenderer.preload();
        }
        WindowManagerGlobal.initialize();
        final Activity a = performLaunchActivity(r, customIntent);
        return a;
    }
// 核心内容，大幅精简
    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        ContextImpl appContext = createBaseContextForActivity(r);
        Activity activity = null;
        java.lang.ClassLoader cl = appContext.getClassLoader();
        // 构造Activity
        activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
        Application app = r.packageInfo.makeApplication(false, mInstrumentation);
        
        // attachBaseContext, 创建PhoneWindow。
        activity.attach(appContext, this, getInstrumentation(), r.token,
                r.ident, app, r.intent, r.activityInfo, title, r.parent,
                r.embeddedID, r.lastNonConfigurationInstances, config,
                r.referrer, r.voiceInteractor, window, r.configCallback,
                r.assistToken, r.shareableActivityToken);
        
        // 进入onCreate
        mInstrumentation.callActivityOnCreate(activity, r.state);
        return activity;
    }
```

到这里Activity的构造、启动就完成了。

### 总结

- 应用启动首先需要解析Intent，确认启动方式、权限、是否能找到对应包、以及目标所需要的任务栈是否满足。再根据需要决定是否创建进程。
- 创建进程由系统服务和Zygote进程通过socket通信，会发送大量的新进程启动参数。新进程启动后，会初始化系统服务、以及虚拟机的一些配置等。进而进入指定的入口类。一般是`ActivityThread`。
- ActivityThread内部有很多类。其中`ApplicationThread`实现了服务端对客户端的一些调用请求，主要方法包括`bindApplication`和`scheduleTransaction`。此外，ActivityThread的主方法会准备Looper并进入循环。任意线程均可以使用`ActivityThread.getHandler()`的`H`实例来发送信息、回调给UI线程。
- ActivityThread负责了不止一个activity的生命周期，而是所有此进程的activity。application的生命周期也是由它来负责。先由AMS对`ApplicationThread`发起`bindApplication`，之后它还会构造`ClientTransactionItem`并通过`ApplicationThread`发起`scheduleTransaction`进而周转Activity的生命周期。

关于`Looper`, `Handler`, `Binder`, 以及渲染流程，请参见“Android”section其他文章。

## Activity启动模式

1. standrad

      - 总是创建新的实例在当前的ActivityTask的栈上。
      - taskAffinity没作用。因为总是在当前的ActivityTask上。
      - 相当于在不同的ActivityTask上可以创建多个实例。

2. singleTop

      - 如果当前已经是同一个实例，则不再创建新实例，而是onNewIntent。其他情况同前者。


3. singleTask

      - 带有clearTop效果。当之前打开过此实例，后面在此task上插入了新的其他页面，又打开此页面时，会复用上次的，并清除所有之间的页面。
      - 如果指定了taskAffinity，与当前的task不符合，则会在新的appTask中的栈上打开。

4. singleInstance

      - 与singleTask相似，但只允许自己出现在这个ActivityTask中。即使此时如果打开一个taskAffinity一样的但模式也是singleInstance的新页面时，这个task仍然只会保留新的页面，此时点击返回会退出。
      - 实际上此时打开任何Activity都会离开此Task，在新的task中打开。包括上面的情况。

## activity生命周期细节

从activity A 打开 activity B，activity A的stop一定是在activity B的resume之后。至于为什么，可以思考下动画，动动脑子就能想得通。

![onStop](../pics/actvity-onStop.png)

此外，对于启动半透明/不透明的Activity来说，即`android:windowIsTranslucent`标志位差异，它的生命周期回调有所不同。如下图所示，即对于半透明的情况，Activity A不会触发onStop。

![](../pics/activity%20lifecycle.png)

![](../pics/activity%20lifecycle%20translucent2.png)

## 拓展阅读

- [Android Application Launch explained](https://medium.com/android-news/android-application-launch-explained-from-zygote-to-your-activity-oncreate-8a8f036864b)
- [ProcessList.java](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/am/ProcessList.java;drc=master;l=2297?q=startprocesslocked&ss=android%2Fplatform%2Fsuperproject)
- [ZygoteProcess.java](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/ZygoteProcess.java;l=626;drc=master;bpv=1;bpt=1) 用于和Zygote进程通信，发送socket数据
- [Zygote.java](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/os/Zygote.java;l=841;drc=master?q=zygote&ss=android%2Fplatform%2Fsuperproject)，收到数据后进入ChildMain方法
- [ZygoteInit.java](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/os/ZygoteInit.java;l=965;drc=master)，`RuntimeInit.applicationInit`方法会加载启动参数列表里`startClass`的`main`方法。此时即`ActivityThread`的`main`。
- [ActivityThread.java](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/ActivityThread.java;l=2558;drc=master;bpv=1;bpt=1)，从`main`方法开始，构造`ActivityThread`实例，Looper.prepare构造线程单例，构造`ActivityThread`的java侧handler即`H`类实例，然后进入Looper循环。此Looper绑定的线程即是主线程。
- [IApplicationThread.aidl](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/IApplicationThread.aidl;l=64;drc=master?q=iapplicationthread&sq=&ss=android%2Fplatform%2Fsuperproject)