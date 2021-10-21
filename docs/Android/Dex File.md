# Dex File

![](http://zjutkz.net/images/dex%E6%96%87%E4%BB%B6%E7%BB%93%E6%9E%84%E5%8F%8A%E5%85%B6%E5%BA%94%E7%94%A8/dex_structure.png)

[参考](https://blog.csdn.net/sbsujjbcy/article/details/52869361)
[参考](http://zjutkz.net/2016/10/27/dex%E6%96%87%E4%BB%B6%E7%BB%93%E6%9E%84%E5%8F%8A%E5%85%B6%E5%BA%94%E7%94%A8/)

三大区：

- Header区，文件基本信息，校验和，table位移
- Table区，如String, Type, Class, Method, Field等table，存储的要么是Data区的偏移量（用于定位到数据区），要么是Table区的索引从而复用、间接定位到数据区。
- Data区，存储实际数据的地方，比如具体字符串数据，代码数据等。

注意区分dex文件和.class文件的差异。下面是一个简化的类比。注意Android不是直接将Java转为dex，而是在.class的基础上转为.dex

java -> xxx.class -> classes.dex -> apk
        yyy.class    classes2.dex

java -> xxx.class -> jar
        yyy.class