Json是常见的序列化反序列化方式。

## JsonField

JsonField是一个我在工作中自创的库，用于映射JSON字段，以及使用注解声明字段的约束检查，会生成代码在构造时检查这些约束是否满足。若不满足则会抛出异常。

### 使用概述

首先介绍下基本用法：
```java
class SomeData{
    @JsonField
    public String stringValue;

    @JsonField
    public int intValueWithDefault = 1;

    @JsonField("jsonkey")
    public float valueWithMappedName;

    @JsonField("key1.key2.key3")
    public boolean jsonPathField;

    @HasJsonField("key1.key2.key3")
    public boolean hasKey3;

    @JsonField
    public AnotherDataType obj;

    class AnotherDataType{
        @JsonField
        public String hh;
    }
}
```
它包含如下基本特性：

- 默认以该字段名作为Json的key。此外可以指定其他key的名称，还可以指定简单的JsonPath字符串来获取嵌套内部的字段。
- HasJsonField的用法略有不同，它仅用来修饰布尔值，表示Json结构中是否包含此key。
- 支持嵌套数据结构处理。
- 字段初始值作为默认值。

除了以上用于常规序列化、反序列化的声明以外，还补充了用于声明字段约束检查的注解，介绍如下：
```java
class SomeData{
    @JsonField
    @Required
    public String requiredField;

    @JsonField
    @ValidString(["valid value1", "valid value2"])
    public String oneOfValidValue;

    @JsonField
    @ValidInt([1,2])
    public int oneOfValidIntValue;

    @JsonField
    @ValidIntRange(min=0,max=Integer.MAX_VALUE)
    public int oneOfValidIntValue2;

    @JsonField
    @NoCast
    public int rejectNumberString;
}
```
它包含如下的字段约束检查能力：

- 必选字段。
- 各基本数据类型有效取值声明。
- 禁止自动类型转换。

在完成上述声明后，Build Project后，会得到所有包含`@JsonField`, `@HasJsonField`这些数据类的对应生成的Builder类。它具有如下方法：
```java
class SomeDataBuilder{
    public static SomeDataBuilder from(JSONObject jsonObject){...}
    public static SomeDataBuilder from(SomeData source){...}
    public JSONObject json(){...}
    public SomeData data(){}
}
```
从而可以进行该类型的序列化、反序列化使用。`obj = SomeDataBuilder.from(json).data()`。如果声明了约束，并且约束检查失败，则反序列化会抛出异常需要自行捕获。即：
```java
try {
    obj = SomeDataBuilder.from(json).data()
}catch(JsonFieldConstraintException e){
    e.constraint // 即检查失败的具体约束。
}
```

### 优劣

它基于APT，并且为了增强灵活性、减少生成的解析逻辑上的冗余，把约束检查、字段解析的具体逻辑均交由工具层完成。而生成的Builder代码只是包含对这些工具层的调用，工具层必须在运行时附带。而后续的维护也可以更多在工具层上完成，而不必频繁更改生成的代码。

优点：

- 相比于无代码生成的方案，如Gson，它从理论上提供了更佳的运行时性能（缺乏数据结论）。
- 声明字段约束可在反序列化时自动执行检查，从业务上带来优势（尽管大部分Json库也有拓展可以支持此类业务）。
- 源码随意拓展，无外部依赖（甚至这个才是出发点）。

缺点：

- apt带来构建时间增加、以及新增类的体积变化。

## Gson

Gson是Java上常用的JSON序列化、反序列化工具。借助运行时反射来获取类与JSON的字段映射关系。

它的特性包括：

- 反射运行时查询映射、支持嵌套、多映射名、字段排除
- 支持拓展

因为它具有拓展能力，可以在反序列化的工作流上增加自定义的处理节点，从而可以实现业务上的一些诉求的定制化。比如数据预处理、字段检查等。

