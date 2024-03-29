> 此文档是很久以前写的，仅作为遗留产物

# Github, Gitlab 的 CI/CD功能

## CI/CD

CI持续集成，CD持续部署。实际上都是在仓库发生一些事件或者周期性的运行一些自定义的脚本。

对于Github上，被定义为了Actions/Workflow功能。对于Gitlab上，被定义为了Pipeline和Schedulers功能。但这些脚本都是通过yml格式写的，具体的语法规范相似但不相同。需要用的时候多参考下文档。

Github的Actions的运行环境是Github直接提供的Azure上的资源（果然当时决定卖给微软还是很有眼光的），自己只需要写脚本就可以了，比Gitlab要方便。Gitlab需要自己或者企业自行提供、注册GitlabRunner（过程还算简单），然后这些跑GitlabRunner的机器还需要装docker或virtualBox等虚拟化类的软件。因为CI/CD这些脚本往往是需要各种各样的构建、部署环境，当然实在不想弄应该也有办法直接在本地上跑。

## Jenkins

我们去看其本质的时候，其实如果只是针对一个特定项目，用不用上面的这些方案都不是特别重要。实际上不过是（一般而言）都是去在代码更新的时候完成一些回归测试等，再通知测试结果。这些本质上还是依赖git自身的webHook就可以做到。只不过CI/CD以及上面所说是最主要的一个使用场景。

WebHook实际上就是当仓库发生一些事件的时候会给自定的url发送请求告知这些事件，并可以修改最终仓库的一些行为。

当我们自己去配置WebHook时，甚至自己写脚本去轮训Git仓库变更时，其实都可以去完成各种自己需要的任务，即使是用Jenkins也不例外。只是上面的CI/CD是针对Github和Gitlab更为简单、封装更好的方案而已。

## Github Actions Secret

GithubActions中自定的workflow脚本难免会用到敏感的如账号、密码等东西，这些可以使用Github Secret来保存，在脚本使用的时候再通过别名引入secrets作为脚本的环境变量即可。这个功能在Gitlab上也有类似产物。

## 思想

git及其类似Github及Gitlab在后期逐渐完善的这一整套流程管理，已经是对大部分的项目（无论项目的类型、项目的规模）都能高度概括、提炼。很多项目，哪怕是类似个人的文章整理、多人旅行计划等等都完全可以依赖这种方式。

比如对于个人文章管理，可以在CI/CD上添加脚本让仓库里的文章全部自动同步到个人网站、其他各类平台的账号、甚至自动推送微博、微信等等。

比如对于多人旅行计划，Github自行可以生成网页，而CI/CD还可以自动把最新的计划通知给所有订阅计划的人。

当然，在此之前，我们甚至还可以加入一些语法分析、语言的静态检查，防止出现错别字等语言错误情况。

总之这种项目的流程和管理，如果深入思考和探究，会发现其灵活度、强大度远远超过想象！你会发现，如果真正充分使用起来这些东西，我们的很多流程中遇到的问题，希望规避的问题都能得到可靠的解决。我很佩服这种真正的CS思维的产物。