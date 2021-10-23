## 原理概述

MMKV是一套基本基于操作系统的、支持多进程的KV存储方案。它以`mmap`形式在进程间共享虚拟内存，`mmap`的文件来源有两种：真实文件、匿名内存fd`/dev/ashmem`。后者是Android Ndk所提供的工具之一。内存布局是一套自定义的格式，类似于Protobuf。对于并发性问题，对真实文件采取的是`flock`文件锁，对匿名内存采取的是`fcntl`“文件记录锁”。

## 相比于其他方案

- SharePreference不支持多进程，没有任何机制、措施可以确保多进程同步。
- 按MMKV文档介绍，ContentProvider一个单独进程管理数据，但是启动慢、访问慢。其他socket, pipe, message queue要至少2次内存拷贝。
- 使用Protobuf形式编码为了更好的性能和更小的空间。（相比Json, xml），一般kv存储文件也不需要考虑人类可读性。

## 代码细节

### 初始化

首先看`MMKV.initialize`，会加载`mmkv`jni lib。`mmkv`jni lib在被加载时有如下工作：
```cpp
// native-bridge.cpp
extern "C" JNIEXPORT JNICALL jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_currentJVM = vm;
    // 获取MMKV java侧单例，注册为全局对象，
    static const char *clsName = "com/tencent/mmkv/MMKV";
    jclass instance = env->FindClass(clsName);
    g_cls = reinterpret_cast<jclass>(env->NewGlobalRef(instance));

    // 注册MMKC.java里的所有native方法，主要绑定到了native-bridge.cpp上，实际处理则再间接交给MMKV.cpp。
    int ret = registerNativeMethods(env, g_cls);
    g_fileID = env->GetFieldID(g_cls, "nativeHandle", "J");

    // 获取crcCheckFail, fileLengthError, contentChange这几个在MMKV.java中的回调方法引用。后续由native触发。
    g_callbackOnCRCFailID = env->GetStaticMethodID(g_cls, "onMMKVCRCCheckFail", "(Ljava/lang/String;)I");
    g_callbackOnFileLengthErrorID = env->GetStaticMethodID(g_cls, "onMMKVFileLengthError", "(Ljava/lang/String;)I");
    g_callbackOnContentChange =
        env->GetStaticMethodID(g_cls, "onContentChangedByOuterProcess", "(Ljava/lang/String;)V");
    ...
}
```
下面来看使用，首先需要获取MMKV实例，MMKV提供了指定`mmkvId`以及`mode`来构造MMKV。`mode`包括`SINGLE_PROCESS_MODE`，`MULTI_PROCESS_MODE`，这两者二选一，并组合上`ASHMEM_MODE`来使用匿名内存工作，否则是以实际文件模式工作。此过程的native部分会由`native-bridge`交给`MMKV.cpp`的若干工具方法构造。精简如下：
```cpp
MMKV::MMKV(const string &mmapID, int size, MMKVMode mode, string *cryptKey, string *rootPath)
    : m_mmapID(mmapedKVKey(mmapID, rootPath)) // historically Android mistakenly use mmapKey as mmapID
    , m_path(mappedKVPathWithID(m_mmapID, mode, rootPath))
    , m_crcPath(crcPathWithID(m_mmapID, mode, rootPath))
    , m_file(new MemoryFile(m_path, size, (mode & MMKV_ASHMEM) ? MMFILE_TYPE_ASHMEM : MMFILE_TYPE_FILE))
    , m_metaFile(new MemoryFile(m_crcPath, DEFAULT_MMAP_SIZE, m_file->m_fileType))
    , m_metaInfo(new MMKVMetaInfo())
    , m_lock(new ThreadLock())
    , m_fileLock(new FileLock(m_metaFile->getFd(), (mode & MMKV_ASHMEM)))
    , m_sharedProcessLock(new InterProcessLock(m_fileLock, SharedLockType))
    , m_exclusiveProcessLock(new InterProcessLock(m_fileLock, ExclusiveLockType))
    , m_isInterProcess((mode & MMKV_MULTI_PROCESS) != 0 || (mode & CONTEXT_MODE_MULTI_PROCESS) != 0) {
    // force use fcntl(), otherwise will conflict with MemoryFile::reloadFromFile()
    m_fileModeLock = new FileLock(m_file->getFd(), true);
    m_sharedProcessModeLock = new InterProcessLock(m_fileModeLock, SharedLockType);
    m_exclusiveProcessModeLock = nullptr;
    {
        m_dic = new MMKVMap();
    }
    m_sharedProcessLock->m_enable = m_isInterProcess;
    m_exclusiveProcessLock->m_enable = m_isInterProcess;
    // sensitive zone
    {
        SCOPED_LOCK(m_sharedProcessLock);
        loadFromFile();
    }
}
```
即构造时也会构造`MemoryFile`、`MMKVMetaInfo`、线程锁、文件锁、进程读锁（共享锁）、进程写锁（互斥锁）。构造`MemoryFile`过程精简如下：
```cpp
// 真实文件模式
MemoryFile::MemoryFile(const string &path, size_t size, FileType fileType)
    : m_name(path), m_fd(-1), m_ptr(nullptr), m_size(0), m_fileType(fileType) {
    if (m_fileType == MMFILE_TYPE_FILE) {
        //从文件开始加载
        reloadFromFile();
    } else {
        //这里主要是不指定ashmemFD的情况下使用了匿名内存模式，会需要手动创建匿名内存
    }
}
// 匿名内存模式
MemoryFile::MemoryFile(int ashmemFD)
    : m_name(""), m_fd(ashmemFD), m_ptr(nullptr), m_size(0), m_fileType(MMFILE_TYPE_ASHMEM) {
        m_name = ASharedMemory_getName(m_fd);
        m_size = ASharedMemory_getSize(m_fd);
        auto ret = mmap();
    }
}
void MemoryFile::reloadFromFile() {
    m_fd = open(m_name.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, S_IRWXU);
        FileLock fileLock(m_fd);
        InterProcessLock lock(&fileLock, ExclusiveLockType);
        SCOPED_LOCK(&lock);

        mmkv::getFileSize(m_fd, m_size);
        // round up to (n * pagesize)
        if (m_size < DEFAULT_MMAP_SIZE || (m_size % DEFAULT_MMAP_SIZE != 0)) {
            size_t roundSize = ((m_size / DEFAULT_MMAP_SIZE) + 1) * DEFAULT_MMAP_SIZE;
            truncate(roundSize);
        } else {
            auto ret = mmap();
    }
}
bool MemoryFile::mmap() {
    m_ptr = (char *) ::mmap(m_ptr, m_size, PROT_READ | PROT_WRITE, MAP_SHARED, m_fd, 0);
}
```
即打开描述符，对指定尺寸包裹为PAGE_SIZE的倍数，然后用此fd调用mmap进行虚拟内存映射，模式可读写且可多进程共享。保存虚拟内存地址。

线程锁使用ndk的工具`pthread_mutex`，文件锁`FileLock`是自定义的工具，会判断是匿名内存还是文件模式。对于文件模式使用`flock`对于匿名内存使用`fcntl`（虽然理论上这两者和文件模式应该没有关系）。进程锁`InterProcessLock`实现基于`FileLock`，并添加了开关，在单进程模式时关闭实际的上锁逻辑。

`MMKV`构造函数最后获取共享锁（读），`loadFromFile`来填充kv内容。

### 写入流程

看下`mmkv.putString`，这会转至`native-bridge.encodeString`，将数据从jni类型转为native类型后，调用`MMKV.cpp`的`set`或者`removeValueForKey`（若没有value时）。`MMKV.cpp`的`set`会进而把value封装为`MMKVBuffer`并调用`setDataForKey`，代码精简如下：
```cpp
// MMKV_IO.cpp
bool MMKV::setDataForKey(MMBuffer &&data, MMKVKey_t key, bool isDataHolder) {
    // 上线程锁、进程互斥锁（写锁）
    SCOPED_LOCK(m_lock);
    SCOPED_LOCK(m_exclusiveProcessLock);
    // 同步下数据，可能会发送数据更新通知
    checkLoadData();
        auto itr = m_dic->find(key);
        if (itr != m_dic->end()) {
            //若有此key
            auto ret = appendDataWithKey(data, itr->second, isDataHolder);
            itr->second = std::move(ret.second);
        } else {
            //若无此key
            auto ret = appendDataWithKey(data, key, isDataHolder);
            m_dic->emplace(key, std::move(ret.second));
        }
    m_hasFullWriteback = false;
}
```
#### 更新通知
```cpp 
void MMKV::checkLoadData() {
    if (m_needLoadFromFile) {
        SCOPED_LOCK(m_sharedProcessLock);
        m_needLoadFromFile = false;
        loadFromFile();
        return;
    }
    if (!m_isInterProcess) {
        return;
    }
    if (!m_metaFile->isFileValid()) {
        return;
    }
    SCOPED_LOCK(m_sharedProcessLock);
    metaInfo.read(m_metaFile->getMemory());
    if (m_metaInfo->m_sequence != metaInfo.m_sequence) {
        // m_sequence代表全量写回的编号，如果不同代表有新的全量写入。此时重新加载
        SCOPED_LOCK(m_sharedProcessLock);

        clearMemoryCache();
        loadFromFile();
        notifyContentChanged();
    } else if (m_metaInfo->m_crcDigest != metaInfo.m_crcDigest) {
        // 序号一致，没有新的全量写回，但摘要不同，要么数据更新了，要么
        SCOPED_LOCK(m_sharedProcessLock);
        size_t fileSize = m_file->getActualFileSize();
        if (m_file->getFileSize() != fileSize) {
            // 文件尺寸有变化，发生了文件截断处理，比如扩容。全部读取
            clearMemoryCache();
            loadFromFile();
        } else {
            // 局部读取，即只需要读取新增数据内容，此时理论上m_actualSize < fileSize。
            partialLoadFromFile();
        }
        notifyContentChanged();
    } // 其他情况没有变更
}
```
#### 开始写入

来看下追加数据的过程：
```cpp
MMKV::doAppendDataWithKey(const MMBuffer &data, const MMBuffer &keyData, bool isDataHolder, uint32_t originKeyLength) {
    ...
    SCOPED_LOCK(m_exclusiveProcessLock);
    // 根据需要扩容内存
    bool hasEnoughSize = ensureMemorySize(size);
    // 写入
    m_output->writeData(keyData);
    m_output->writeData(data); // note: write size of data
    auto ptr = (uint8_t *) m_file->getMemory() + Fixed32Size + m_actualSize;
    m_actualSize += size;
    // 更新摘要
    updateCRCDigest(ptr, size);
    ...
}
```
#### 文件截断、扩容
```cpp
bool MMKV::ensureMemorySize(size_t newSize) {
    if (newSize >= m_output->spaceLeft() || (m_crypter ? m_dicCrypt->empty() : m_dic->empty())) {
        // try a full rewrite to make space
        // 1. no space for a full rewrite, double it
        // 2. or space is not large enough for future usage, double it to avoid frequently full rewrite
        if (lenNeeded >= fileSize || (lenNeeded + futureUsage) >= fileSize) {
            size_t oldSize = fileSize;
            do {
                fileSize *= 2;
            } while (lenNeeded + futureUsage >= fileSize);
            // if we can't extend size, rollback to old state
            if (!m_file->truncate(fileSize)) {
            }
        }
        return doFullWriteBack(move(preparedData), nullptr);
    }
    return true;
}
// 按指定尺寸截断文件，对匿名内存会失败，对真实文件则使用`ftruncate`，之后为新内存填0，重新映射虚拟内存。
bool MemoryFile::truncate(size_t size) {
    if (m_fd < 0) {
        return false;
    }
    if (size == m_size) {
        return true;
    }
#    ifdef MMKV_ANDROID
        if (size > m_size) {
            MMKVError("ashmem %s reach size limit:%zu, consider configure with larger size", m_name.c_str(), m_size);
        }
        return false;
    }
#    endif // MMKV_ANDROID

    auto oldSize = m_size;
    m_size = size;
    // round up to (n * pagesize)
    if (m_size < DEFAULT_MMAP_SIZE || (m_size % DEFAULT_MMAP_SIZE != 0)) {
        m_size = ((m_size / DEFAULT_MMAP_SIZE) + 1) * DEFAULT_MMAP_SIZE;
    }

    if (::ftruncate(m_fd, static_cast<off_t>(m_size)) != 0) {
    }
    if (m_size > oldSize) {
        if (!zeroFillFile(m_fd, oldSize, m_size - oldSize)) {
        }
    }

    if (m_ptr) {
        if (munmap(m_ptr, oldSize) != 0) {
    }
    auto ret = mmap();
}
```

## 总结

MMKV充分利用了操作系统的基本能力，以及针对移动端应用的场景之下，设计了此KV存储库，性能快、空间效率高、多进程友好。也支持加密数据存储，对安全性上也补强了支持。是一款优秀的kv方案和思路。若想认真研读此库最好对Linux标准库的调用有一定经验，目前对我是比较缺失的。

## 拓展阅读

- [MMKV Github](https://github.com/Tencent/MMKV)
- [MMKV Github 多进程WIKI](https://github.com/Tencent/MMKV/wiki/android_ipc)
- [Memory Android Ndk](https://developer.android.com/ndk/reference/group/memory)
- [CRC](https://zh.wikipedia.org/wiki/%E5%BE%AA%E7%92%B0%E5%86%97%E9%A4%98%E6%A0%A1%E9%A9%97)
- protobuf请参阅“编码”section。
- mmap、flock、fcntl、ftruncate 请参阅“操作系统”section。