makefile是一个类似shell脚本文件，用于借助gnu make工具，靠`make`指令来构建出目标产物。

cmake则是构建系统的生成器，借助gnu cmake工具，在描述一些规则后来生成出makefile或者其他类型的构建系统文件，包括Ninja构建文件、ide solution等。

## makefile

基本规则是：

```makefile
target ... : prerequisites ...
    command
```
当target不存在、或者prerequisties的文件修改时间比target文件更新，则会执行command。target除了可以是文件名以外，还可以是label，可以通过make label来执行一些特定的行为。

此外常见的还包括：

- 引入变量，来方便维护文件列表。
- 引入其他makefile，来构造更复杂的项目

## cmake


## 阅读更多

- [makefile tutorial](https://seisman.github.io/how-to-write-makefile/introduction.html)
- [cmake tutorial](https://cmake.org/cmake/help/latest/guide/tutorial/index.html)
