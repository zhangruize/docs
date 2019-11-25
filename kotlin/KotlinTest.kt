
fun main(args: Array<String>) {
    // paramWithDefaultValue("zrz")
    paramWithDefaultValue(param2="5555")
    paramWithDefaultValue(param="zrz")
    paramWithDefaultValue()
    paramWithDefaultValue("zrz","5555")
}

fun paramWithDefaultValue(param:String = "def", param2:String="5113") = println(param+param2)



