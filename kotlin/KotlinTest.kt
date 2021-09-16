
fun main2(args: Array<String>) {
    // paramWithDefaultValue("zrz")
    paramWithDefaultValue(param3="3333")
    paramWithDefaultValue(param2="5555")
    paramWithDefaultValue(param="zrz")
    paramWithDefaultValue()
    paramWithDefaultValue("111",param3="zrz")
}

private fun paramWithDefaultValue(param:String = "def", param2:String="5113", param3:String="333") = println(param+param2+param3)


