# MultiDex理解

核心点：

- 根据sp以及之前解压的文件目录判断是否需要重新解压，需要的话，按规则从data/app下的apk文件（applicationInfo.sourceDir）中通过类似classes2.dex寻找ZipEntry，并解压到applicationInfo.dataDir即app的数据目录下特定子目录存放（方便之后使用而不是每次都解压）
- 再依赖反射使用PathClassLoader下的pathList的makeDexElement方法生成dex元素
- 最后通过反射获取把追加的、完整的dexElement赋值给pathList的dexElements。从而完成。

```plain
sourceDir
/data/app/jd.cdyjy.overseas.jd_id_shopping_cart.app-fnmMyjqWlC9sW43gkc4_eg==/base.apk

dataDir
/data/user/0/jd.cdyjy.overseas.jd_id_shopping_cart.app

```