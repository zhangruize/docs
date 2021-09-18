# binder

## 背景概述

一种基于Linux系统的进程间通信方式。起源于OpenBinder，经过多次公司转手开发，binder驱动程序在2014年左右已经合入到linux内核。

特点：
- 单次拷贝。基于每个进程(ProcessState::self()->init->open_driver(), mmap)打开Binder驱动文件并mmap了一部分内存，以用于接受transaction数据。乍一看，需要copy_from_user来把from的用户进程数据拷贝到目标进程的内核，而再copy_to_user以让目标进程的用户空间可以访问。但实际因为mmap的原因，目标进程在copy_form_user后就可以访问通信数据了。而copy_to_user实际上是在from进程读取reply的时候使用。因为reply的数据是在目标进程的用户空间（mmap区域里），此时只需要一次copy_to_user即可让From进程的用户空间访问reply。**这个copy的数据是binder_transaction_data类型。可以看到在IPCThreadState.writeTransactionData中在封装这个数据，而在binder.c#binder_thread_write对cmd:BC_TRANSACTION的处理就是把tr数据拷贝**


- 面向对象。开发者友好。native或者java侧，都可以拿到服务接口的proxy（native靠模板和宏展开，java靠aidl生成），可以直接像调用本地对象的方法一样访问服务进程的功能。

- 安全/可订阅（死亡信息）
  
争议：

- 由于linux缺乏对高速ipc的支持，出现了多种针对高速ipc的方式。而binder的争议主要在于，对同一个设备文件标识符，dev/binder，在不同进程中的行为是不一样的。这个和大部分人的预期不符。

（anyway，还是最终合入了，并且也被劝告它并不好用，你得搞清楚，用的话自己负责）

## 设计概述

每个进程都可以通过访问dev/binder 标识符，来和binder驱动程序通信。而binder驱动程序提供了ipc的所需所有功能接口。

情景化思考：

ipc考虑的问题在于，对于Proc A ，我要和一个服务B通信，这个服务B既包括系统内置的系统服务，也包括用户应用提供的服务。而服务会动态变化，因此对于Proc A来说，想要硬编码一个服务地址，并不可取。合理的设计很容易想到应该是有一个固定的服务地址，它所提供的服务之一是查询其他服务的地址。

而ServiceManager，对应的handle:0，也就是这个固定地址的系统服务，所扮演的角色就是服务管理器。它自己需要从binder驱动的ioctrl来设置自己是管理者。而其他的进程需要binder驱动的ioctrl来和serviceManager通信，调用它的addService服务。而对于Proc A，则是依靠binder驱动ioctl来从serviceManager中获取某服务，比如传入特定枚举，以获得该枚举对应服务的IBinder，再转为Proxy来调用目标服务的功能。调用目标服务的功能时，本质上和serviceManager通信也没区别。

## 代码细节

IBinder中包含transact等方法，有两个关键子类：
- BBinder（理解为Service的实现端），也就是真正实现了服务接口，一般在实现类中，等待Binder框架调用其onTransact->并解包数据，调用的接口方法上。
- BpBinder（理解为Client端），也就是服务接口的Proxy。客户端调用Proxy，Proxy负责封装数据、转为Parcel，进行后续的transact。

Parcel在writeBinder时，如果是写入BBinder，也就是服务实现端，会记录binder_object_type: binder_type_binder。若是代理端，则是binder_type_handle.这两种类型，会在binder驱动的binder_transaction方法中对应不同的行为。




ProcessState::self()->init

open_driver

mmap


### server

startThreadPool->...->IPCThreadState.joinThreadPool()

IPCThreadState joinThreadPool-> getAndExecuteCommand()-> (talkWithDriver, executeCommand)

![](ipcthreadstatep1.png)

talkWithDriver(https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/binder/IPCThreadState.cpp;drc=master;l=1003)

    -> ioctl(mProcess->mDriverFD, BINDER_WRITE_READ, &bwr) 

executeCommand:
    case BR_TRANSACTION:
    (BBinder)(tr.cookie)->transact(...)


### client

...->IPCThreadState.transact(https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/binder/IPCThreadState.cpp;drc=master;bpv=0;bpt=1;l=712)

![](ipcthreadstate_transact.png)

    - writeTransactionData() (# wrap data...)
    - waitForResponse(if not one way, write to reply, else just check result.)
        - talkWithDriver


### overview

![](binder_ipc_process.jpg)


## Binder驱动工作方式

BinderIPC的具体transaction数据写入过程，以及binder驱动所提供的所有功能，需要查看binder驱动程序的代码。


Binder驱动代码已经合入到linux内核，所以无法在android source看到，而是需要去Linux的内核仓库查看。
binder.c(https://github.com/torvalds/linux/blob/master/drivers/android/binder.c)

和其他字符设备不同，binder的主要工作并不和read/write有关，而是借助ioctl的调用。



## linux驱动、系统调用背景知识



alloc, malloc, calloc
alloc在栈上分配内存。
malloc是只分配内存，但需要Memset后才能访问。
而calloc是分配内存后，可以直接访问（都填0）。后两者是在进程堆上分配。

kzalloc, kmalloc
k前缀表示是从Kernel调用，用以区分从用户空间的调用。都是在内存堆中分配。前者会清零，后者不会。

current 当前的进程task_struct。
关于pid和tid。在linux，线程在内核所维护的任务数据结构也是task_struct，所以tid就是task_struct里面的pid。
而进程id（pid）实际上是tgid。也就是线程组id。也是task_struct的字段之一。

因此会看到在binder driver里，binder_ioctl中对binder_thread的获取方式很简单粗暴，在flip->privateData(BinderProc)的红黑树里根据current->pid来比对查询。如果查询不到，则创建新的binder_thread节点插入其中。




## 一个标准过程

### ServiceManager启动

1. 第一个被启动。除了对binder驱动进行open, mmap之外，还会通过几次ioctl来把自己设置为context manager。此后，会进入binder_loop方法，不断的等待binder驱动数据，再解析执行指令。


### 服务注册

服务Servcice B注册过程如下：

1. Service B 获取ServiceManager。
2. 调用IServiceManager.addService。


### 应用进程启动

应用进程Proc A 需要通过Binder跨进程来调用Service B 的过程如下：

1. Proc A 获取ServiceManager
   1. 此过程仅需要ProcessState参与。ServiceManager会有一些本地的包装类，以确保在本地只需要创建一个ServiceManagerProxy单例即可。第一次创建时，通过ProcessState::selft()->getStrongProxyForHandle(null)即可，也就是创建了一个handle:0的BpBinder。
   - 注意：大部分BpBinder的创建都是由ProcessState进行的，因为它维护了当前进程下的BpBinder列表
2. Proc A 调用IServiceManager的getService(...)方法，获取Service B的Proxy。
   1. 此时因为ServiceManagerProxy的本地缓存还没有此类BpBinder（这里的ServiceManagerProxy泛指ServiceManager的本地包装类），需要进行ipc获取。因此需要由IServiceManager发起一次transact来调用服务端的getService方法。其内部Code是GET_SERVICE。
   2. Proc A在transact过程中，out数据主要是要查询的服务名称。transact的过程主要是对方法调用参数parcel序列化，BpBinder.transact->...IPCThreadState.transact->talkWithDriver, waitForResponse。而talkWithDriver又是ioctl调用binder_write_read过程，此时的事务数据已经封装为了bwr(binder_write_read)结构体。
   3. binder驱动侧会通过ioctl调用的描述符参数flip的privateData，取出Binder_proc结构体，它记录了Binder在该进程上的信息。此字段初始化与ProcessState的初始化有关，详见上面，不再赘述。再从binder_proc的结构体中获取当前线程结构体信息，它是通过binder_proc里面的binder_thread红黑树记录，以current->pid比对。此时已经有了proc,thread两个主要环境数据，然后根据不同ioctl的cmd来处理。
   4. binder_ioctl->binder_ioctl_write_read拷贝了transaction数据之后进入binder_transaction方法。
   5. binder_transaction：
      1. 获取targetNode, 以此得到targetProc, targetThread, targetBuffer，也就是确定要写入的目标缓存地址。
         1. 如果不是回复结果，则看transactionData里若有target.handle，那么直接在当前进程proc的binder_node红黑树记录里面去找就可以了，比对handle。若没有，也就是0，此时把binder驱动中context_manager也就是serviceManager作为targetNode。
         2. 若是回复，那么直接从thread的transaction_stack里的From内容就可以找到targetThread等。
      2. 分配空间，拷贝transactionData数据，到target_proc->alloc。
      3. 在proc，targetProc记录适当的binderNode。主要是在注册服务时，SerivceManager进程的proc记录服务进程的Binder_node_ref。以及在获取服务时，在请求进程的proc记录服务提供进程的binder_node_ref。以此可以确保后面再进行ioctl的时候，binder driver能知道发起进程的handle所指的目标binder_node到底是什么、在哪里。
      4. 唤醒目标线程。
   6. 等待结果。之后读取结果数据。Parcel解包的时候，会readStrongBinder->ProcessState::getStrongProxyForHandle(handle)，ProcessState会检查是否已经有维护，若没有，则构造一个BpBinder返回。
   7. 将BpBinder转为Proxy，对于native的转化借助宏和模板，对于java的转化，借助aidl生成的Proxy类。此时，就可以对BpBinder调用其transact方法，来得到对应的功能了。也可以使用Proxy直接调用接口方法。
3. 用Proxy调用Service B 方法。
   1. 此时流转到BpBinder->transact，同2.2。不外乎写parcel和解parcel时数据不同，以及handle不同也就是在binder_transaction时，得到的target_node不同，即要写入的目标地址不同、唤醒的线程不同。
