## 概述

可以访问[在线链接](https://zhangruize.github.io/tp/art/complex-gradient/)，手动刷新页面来重新得到随机图案。

此项目得益于Code pen的类似项目启发，可以用Canvas API，结合图层模式（即`CanvasRenderingContext2D.globalCompositeOperation`）、以及对阶段性步骤先导出为Image，后续再引入Image编排绘制，可以把一个简单的线性渐变做出类似的图案。这类图像在2000-2013年左右是个人很痴迷的一类类似Windows Vista追捧的光线感的图片，那个时候还尝试用PhotoShop来制作，但相比这个随机生成，后者效率明显高多了。

## 截图

![](../pics/lineargradient1.png)

## 拓展阅读

- [在线体验](https://zhangruize.github.io/tp/art/complex-gradient/)，可以直接看源码。
- [CanvasRenderingContext2D.globalCompositeOperation](https://developer.mozilla.org/en-US/docs/Web/API/CanvasRenderingContext2D/globalCompositeOperation) mdn介绍