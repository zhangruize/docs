本文档旨在帮助你快速上手、搭建一个现代的Javascript环境。高手可以直接忽略。

> 本文Js环境主要指基于Node的环境。文本编辑器使用VSCode。

一开始面临各种配置文件，比如package.json, tsconfig.json, webpack.js xxx.json/js这种各式各样的dsl文件让我觉得力不从心，但后来发现大部分的工具都提供了init指令用于友好、交互地初始化一份本地工作环境。因为有时候诉求很简单，只想要一份简单的配置能Run起来，此时一定记得npx ... init/--init，这样就不必到处去找一个配置样本再自定义了。

一般我需要的环境配置如下：

最基本的包括：

- node.js:
    - cd working_space
    - npm init
- typescript:
    - npm install -D typescript 
    - npx tsc --init

如果需要watch、需要出捆绑包、需要面向web的话：
> 如果只是简单watch，可以用node手写

- webpack:
    - npm install -D webpack
    - npx webpack init
- webpack-shell-plugin-next

之后根据需要，再关注编辑目录下的tsconfig.json, package.json, webpack.js即可。若需要高级用法，再查官网定义。