## CPU/MPU/MCU/SOC/DSP

CPU（Central Processing Unit，中央处理器）发展出来三个分枝，一个是DSP（Digital Signal Processing/Processor，数字信号处理），另外两个是MCU（Micro Control Unit，微控制器单元）和MPU（Micro Processor Unit，微处理器单元）。


1、CPU(Central Processing Unit)，是一台计算机的运算核心和控制核心。CPU由运算器、控制器和寄存器及实现它们之间联系的数据、控制及状态的总线构成。差不多所有的CPU的运作原理可分为四个阶段：提取(Fetch)、解码(Decode)、执行(Execute)和写回(Writeback)。 CPU从存储器或高速缓冲存储器中取出指令，放入指令寄存器，并对指令译码，并执行指令。所谓的计算机的可编程性主要是指对CPU的编程。

2、MPU (Micro Processor Unit)，叫微处理器(不是微控制器)，通常代表一个功能强大的CPU(暂且理解为增强版的CPU吧),但不是为任何已有的特定计算目的而设计的芯片。这种芯片往往是个人计算机和高端工作站的核心CPU。Intel X86，ARM的一些Cortex-A芯片如飞思卡尔i.MX6、全志A20、TI AM335X等都属于MPU。

3、MCU(Micro Control Unit)，叫微控制器，是指随着大规模集成电路的出现及其发展，将计算机的CPU、RAM、ROM、定时计数器和多种I/O接口集成在一片芯片上，形成芯片级的芯片，比如51，AVR、Cortex-M这些芯片，内部除了CPU外还有RAM、ROM，可以直接加简单的外围器件(电阻，电容)就可以运行代码了。而如x86、ARM这些MPU就不能直接放代码了，它只不过是增强版的CPU，所以得添加RAM，ROM。

4、SOC(System on Chip)，指的是片上系统，MCU只是芯片级的芯片，而SOC是系统级的芯片，它既MCU(51，avr)那样有内置RAM、ROM同时又像MPU那样强大，不单单是放简单的代码，可以放系统级的代码，也就是说可以运行操作系统(将就认为是MCU集成化与MPU强处理力各优点二合一)。

5、DSP运算能力强，擅长很多的重复数据运算

 

微控制器在经过这几年不断地研究,发展,历经4位,8位,到现在的16位及32位,甚至64位。产品的成熟度,以及投入厂商之多,应用范围之广,真可谓之空前。目前在国外大厂因开发较早,产品线广,所以技术领先,而本土厂商则以多功能为产品导向取胜。但不可讳言的,本土厂商的价格战是对外商造成威胁的关键因素。 由于制程的改进，8位MCU与4位MCU价差相去无几，8位已渐成为市场主流；

目前4位MCU大部份应用在计算器、车用仪表、车用防盗装置、呼叫器、无线电话、CD播放器、LCD驱动控制器、LCD游戏机、儿童玩具、磅秤、充电器、胎压计、温湿度计、遥控器及傻瓜相机等；

8位MCU大部份应用在电表、马达控制器、电动玩具机、变频式冷气机、呼叫器、传真机、来电辨识器（CallerID）、电话录音机、CRT显示器、键盘及USB等；

16位MCU大部份应用在行动电话、数字相机及摄录放影机等；

32位MCU大部份应用在Modem、GPS、PDA、HPC、STB、Hub、Bridge、Router、工作站、ISDN电话、激光打印机与彩色传真机；

64位MCU大部份应用在高阶工作站、多媒体互动系统、高级电视游乐器（如SEGA的Dreamcast及Nintendo的GameBoy）及高级终端机等。

> 原文 https://blog.csdn.net/mao834099514/article/details/106720704

## x86/ARM

苹果宣布决定从英特尔 CPU 转向自己的 ARM 芯片。有什么区别？新的 Apple Silicon 芯片基于 ARM CPU，就像目前用于 iPhone 和 iPad 的 CPU。英特尔芯片使用英特尔专有的 x86 架构。

ARM 是RISC 架构。RISC 代表精简指令集计算。这意味着 CPU 可以使用的指令数量有限。结果，每条指令在一个周期内运行，指令更简单。同时，x86 是一种 CISC 架构，代表复杂指令集计算。

这意味着它有更多的指令。确切的数字取决于您如何计算它们，但 x86-64至少有 981 条指令。另一方面，ARM接近 50 个（ARM 的实际文档很难找到，因为它只是半开放的）。其中一些指令将需要一个以上的周期才能执行。但是，有些指令可以完成许多 RISC 指令的工作。

ARM 的另一个好处是它是一个半开放式架构。很少有公司生产 x86 处理器，因为英特尔已将其设为闭源。另一方面，ARM 实际上并不制造自己的 CPU。他们将设计授权给其他想要制造自己的 CPU 的公司。苹果就是其中之一。苹果能够定制他们的芯片以在他们的平台上更好地工作。这大概就是iPhone 在基准测试中表现出色的原因。

ARM 设计得更小、更节能，并且产生的热量更少。这使其非常适合移动设备，如智能手机。小尺寸使其非常适合小型设备。能源效率使设备的电池寿命更长。较低的热量对于不断被持有的设备是有益的。

笔记本电脑也有同样的好处。苹果在2020 年、2018 年、2015年及更早的年份一直存在过热问题。ARM 将允许 Apple 让他们的 MacBook 更酷，所以他们不会遇到节流问题。这将使他们能够制造具有更长电池寿命的设备。苹果的笔记本电脑也有可能变得更小。

ARM的缺点
一个问题是 x86 程序无法在 ARM 上运行。程序需要完全重写才能在 Apple 的新机器上运行。大多数编程语言都可以很好地针对 ARM。任何当前维护的程序都应该没有什么问题。不过，任何用Assembly编写的东西都需要重写才能在 ARM 上运行。

主要问题是不再更新的程序。Apple 有Rosetta 2，它可以运行 x86 应用程序。然而，众所周知，Rosetta 1 的运行速度比在原始硬件上慢得多。这是必然的。Rosetta 需要将 x86 指令实时翻译成 ARM 指令。公平地说，Java 可以从字节码转换为其他所有内容，而且它似乎可以正常工作。尽管如此，如果微软也决定转向 ARM，似乎很难想象能够玩 2010 年代的游戏。

还有一个速度问题。由于 ARM 的指令较少，因此开发人员需要使用更多的指令。例如，ARM 通常没有除法指令。即使是最高效的除法算法也很复杂，很多 ARM CPU 都没有实现。在这些 CPU 上，您必须使用其他指令进行划分。由于您使用其他指令来伪造除法，因此最终需要更多周期。这甚至可能比 CISC 指令集慢。

> 原文 https://www.section.io/engineering-education/arm-x86/#:~:text=ARM%20is%20a%20RISC%20architecture,of%20instructions%20it%20can%20use.&text=Meanwhile%2C%20x86%20is%20a%20CISC,for%20Complex%20Instruction%20Set%20Computing.

## CISC/RISC/MISC

CISC: 复杂指令集, RISC: 简单指令集， MISC: 最小指令集

> wiki: https://en.wikipedia.org/wiki/Reduced_instruction_set_computer

## esp32/stm32

如上所说，一般来说，这两者的芯片产品都是mcu，即包含了除了cpu以外的更多模块，如wifi，蓝牙等，算是CoC，chips on chips。而差异在于，esp32的cpu并不是arm架构的cpu，由此ISC也和ARM的有所差异。这也是说，如果某些库只有arm的编译版本，那么并不能在esp上使用。除此以外，对于一些依赖了汇编的代码，也需要更多的ISC兼容性。

> esp 所用的Tensilica mcu的ISC： https://en.wikipedia.org/wiki/Tensilica#Xtensa_instruction_set

