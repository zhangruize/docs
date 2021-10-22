# Glide

> 此文章待完善、较老

## EngineResource

- 使用计数增加时机：
- 使用计数减少时机：

## Glide缓存概述

按介质分内存缓存、硬盘缓存。内存缓存分ActiveResources和MemoryCache，硬盘缓存分ResourceCache和DataCache。

查询顺序也是按上面所说的顺序。

### ActiveResources

- 使用HashMap+WeakReference(ReferenceQueue)来维护。
- 添加时机：当有任何资源准备就绪，且不处在此map中。
- 删除时机：当EngineResource的使用计数为0，或GC发生时，WeakReference被回收时。
- 删除后会放到MemoryCache之中
- 索引Key

### MemoryCache

- 使用LinkedHashMap实现的LruCache。
- 添加时机：ActiveResources剔除时候。
- 删除时机：Lru剔除算法
- 索引Key

### ResourceCache

- 

### DataCache


## BitmapPool


## Target
- 

## 配置方式代码设计

- 由于是单例的设计，只需要一次初始化配置。注解标记自定义初始化实现类。使用了APT方案。

## 加载模式代码设计

- 维护了一个注册表，用于查询不同的加载目标、数据源类型对应的加载器类。可以自定义添加、替换各种加载器的实现，甚至是最终的`GlideUrl.class, InputStream.class`的实现，从而可以把网络加载过程使用不同的网络库实现。
- 对于加载器进行了抽象、加载器完成的callback也进行了抽象。从而可以让Glide的缓存机制脱离于加载器的实现，可以正常运行。
