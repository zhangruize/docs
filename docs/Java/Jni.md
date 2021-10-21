## 方法注入

有两种注入jni的方式，一种是正常声明调用，需要先`System.loadLibrary/load`。另一种是用`JNIEnv的registerNatives`方法，参考代码如下：

```
static JNINativeMethod methods[] = {
    {"hashCode",    "()I",                    (void *)&JVM_IHashCode},
    {"wait",        "(J)V",                   (void *)&JVM_MonitorWait},
    {"notify",      "()V",                    (void *)&JVM_MonitorNotify},
    {"notifyAll",   "()V",                    (void *)&JVM_MonitorNotifyAll},
    {"clone",       "()Ljava/lang/Object;",   (void *)&JVM_Clone},
};

JNIEXPORT void JNICALL
Java_java_lang_Object_registerNatives(JNIEnv *env, jclass cls)
{
    (*env)->RegisterNatives(env, cls,
                            methods, sizeof(methods)/sizeof(methods[0]));
}
```

## 拓展阅读

- [Stackoverflow](https://stackoverflow.com/questions/1010645/what-does-the-registernatives-method-do)