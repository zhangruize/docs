ViewModel用于解决Activity/Fragment（依附于Activity）在`ConfigurationChanged`或系统销毁、自动恢复时，跨实例场景下，让数据得到更好持久化的方案。使其周期和“逻辑页面”保持一致，从而避免类似旋转了屏幕就会导致页面恢复初始效果的情况。生命周期如下图说明：

![](../pics/viewmodel%20lifecycle.png)