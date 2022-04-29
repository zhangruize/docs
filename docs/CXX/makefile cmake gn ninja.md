## 概述

makefile是一个类似shell脚本文件，用于借助gnu make工具，靠`make`指令来构建出目标产物。

cmake则是构建系统的生成器，借助gnu cmake工具，在描述一些规则后来生成出makefile或者其他类型的构建系统文件。

ninja、gn则是Google出产的cxx构建工具。相比make/cmake，ninja/gn旨在更高效、并且尽可能避免make/cmake那些隐晦的隐式宏、变量等。ninja可以类比make，而gn可以类比cmake。

## makefile

文件名为`makefile`或`xxx.mk`就会被认为是makefile了。文件的基本规则是：

```makefile
target ... : prerequisites ...
    command
```
当target不存在、或者prerequisties的文件修改时间比target文件更新，则会执行command。target除了可以是文件名以外，还可以是label，可以通过make label来执行一些特定的行为。

此外常见的还包括：

- 引入变量，来方便维护文件列表。
- 引入其他makefile，来构造更复杂的项目

makefile中的cc, ld等都可以进一步指定具体的工具，$(make)则可以进一步嵌套子目录编译。

### 示例：简单编译
```makefile
blah: blah.o
    cc blah.o -o blah # Runs third

blah.o: blah.c
    cc -c blah.c -o blah.o # Runs second

blah.c:
	echo "int main() { return 0; }" > blah.c # Runs first
```

### 示例：引入子目录
根目录
```makefile
new_contents = "hello:\n\\techo \$$(cooly)"

all:
	mkdir -p subdir
	echo $(new_contents) | sed -e 's/^ //' > subdir/makefile
	@echo "---MAKEFILE CONTENTS---"
	@cd subdir && cat makefile
	@echo "---END MAKEFILE CONTENTS---"
	cd subdir && $(MAKE)

# Note that variables and exports. They are set/affected globally.
cooly = "The subdirectory can see me!"
export cooly
# This would nullify the line above: unexport cooly

clean:
	rm -rf subdir
```
子目录subdir：
```makefile
hello:
	echo $(cooly)

```

## cmake

文件名为`cmakelists.txt`，就会被认为是cmake文件了。它包含了一系列的宏来描述一个cxx的项目构建：

### 示例：生成选项宏，增加子目录
```cmake
cmake_minimum_required(VERSION 3.10)
project(Step2 VERSION 0.1)

option(USE_ALIB "use alib ?" ON)
configure_file(Step1Config.h.in Step1Config.h)

if(USE_ALIB)
    add_subdirectory(alib)
    list(APPEND EXTRA_LIBS ALib)
endif()

add_executable(Step2 step1.cpp)
target_link_libraries(Step2 PUBLIC ${EXTRA_LIBS})
target_include_directories(Step2 PUBLIC 
    "${PROJECT_BINARY_DIR}"
)

install(TARGETS Step2 DESTINATION bin)
```
子目录alib:
```cmake
add_library(ALib alib.cpp)
target_include_directories(ALib INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})

install(TARGETS ALib DESTINATION lib)
install(FILES alib.h DESTINATION include)
```

### 示例：引入外部头文件、库
在`brew install cjson`后，即可在获得cjson的头文件和库。如下可以引入到构建中：
```cmake
cmake_minimum_required(VERSION 3.10)
project(CJsonPlay VERSION 0.1)
add_executable(CJsonPlay play.cpp)
target_include_directories(CJsonPlay PUBLIC "/usr/local/include/")
find_library(CJson cjson)
target_link_libraries(CJsonPlay PUBLIC "${CJson}")
```
### 示例：macOS引入预构建Skia
macOS上有时候需要引入一些系统库，如下`-framework xxxx`部分。
```cmake
cmake_minimum_required(VERSION 3.10)
project(SkiaPlay VERSION 0.1)
add_executable(SkiaPlay play.cpp)
target_compile_features(SkiaPlay PRIVATE cxx_std_17)
target_include_directories(SkiaPlay PUBLIC "${PROJECT_SOURCE_DIR}")
find_library(Skia skia HINTS "/Users/zhangruize/fav-libs/skia/out/Static")
target_link_libraries(SkiaPlay PUBLIC "${Skia}" 
"-framework CoreFoundation"
"-framework CoreGraphics"
"-framework CoreText"
"-framework ImageIO"
"-framework ApplicationServices"
)
```

### 示例：安卓上引入ndk库
安卓引入ndk，一般在module的build.gradle中：
```gradle
android{
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1' // 版本按需设置。如果ndk自带的cmake版本不符合要求，则gradle plugin会在环境变量和local.properties: cmake.dir中寻找符合要求的cmake。
        }
    }
    ndkVersion '22.1.7171670'
}
```
minSdkVersion会决定所使用的ndk头文件、链接的库。不同的minSdkVersion下，可能库文件名有差异。如`#include <android/native_window.h>`在不同的版本时，会`-landroid`或`-lnativewindow`即位于`libandroid`或`libnativewindow`。

## ninja

它可以与`makefile`类比：
```ninja
ninja_required_version = 1.7.2

rule gn
  command = ../../../../tools/gn --root=./.. -q --regeneration gen .
  pool = console
  description = Regenerating ninja files

rule hh
  command = echo "hhh"

build hh2: hh

build build.ninja: gn
  generator = 1
  depfile = build.ninja.d

subninja toolchain.ninja

build $:hello: phony ./hello

build all: phony $
    ./hello

default all
```
结构大致如下：
```
config

rule name
    config...

build name: ruleName
    config...
```

## gn

在项目的根目录中建立`.gn`文件，一般格式如下：
```gn
buildconfig = "//gn/BUILDCONFIG.gn"

default_args = {
}
```
其中gn目录下：
```gn
set_default_toolchain("//gn/toolchains:gcc_like")
cflags_cc = ["-std=c++11"]
```
注意，gn不会默认采用任何toolchain，所有toolchain都需要手动指定。在toolchain目录下，build.gn:
```gn
template("gcc_like_toolchain") {
  toolchain(target_name) {
    ar = invoker.ar
    cc = invoker.cc
    cxx = invoker.cxx
    link = invoker.link
    lib_switch = "-l"
    lib_dir_switch = "-L"

    tool("cc") {
      depfile = "{{output}}.d"
      command = "$cc -MD -MF $depfile {{defines}} {{include_dirs}} {{cflags}} {{cflags_c}} -c {{source}} -o {{output}}"
      depsformat = "gcc"
      outputs =
          [ "{{source_out_dir}}/{{target_output_name}}.{{source_name_part}}.o" ]
      description = "compile {{source}}"
    }

    tool("cxx") {
      depfile = "{{output}}.d"
      command = " $cxx -MD -MF $depfile {{defines}} {{include_dirs}} {{cflags}} {{cflags_cc}} -c {{source}} -o {{output}}"
      depsformat = "gcc"
      outputs =
          [ "{{source_out_dir}}/{{target_output_name}}.{{source_name_part}}.o" ]
      description = "compile {{source}}"
    }

    tool("objc") {
      depfile = "{{output}}.d"
      command = " $cc -MD -MF $depfile {{defines}} {{include_dirs}} {{framework_dirs}} {{cflags}} {{cflags_objc}} -c {{source}} -o {{output}}"
      depsformat = "gcc"
      outputs =
          [ "{{source_out_dir}}/{{target_output_name}}.{{source_name_part}}.o" ]
      description = "compile {{source}}"
    }

    tool("objcxx") {
      depfile = "{{output}}.d"
      command = " $cxx -MD -MF $depfile {{defines}} {{include_dirs}} {{framework_dirs}} {{cflags}} {{cflags_cc}} {{cflags_objcc}} -c {{source}} -o {{output}}"
      depsformat = "gcc"
      outputs =
          [ "{{source_out_dir}}/{{target_output_name}}.{{source_name_part}}.o" ]
      description = "compile {{source}}"
    }

    tool("asm") {
      depfile = "{{output}}.d"
      command = " $cc -MD -MF $depfile {{defines}} {{include_dirs}} {{asmflags}} -c {{source}} -o {{output}}"
      depsformat = "gcc"
      outputs =
          [ "{{source_out_dir}}/{{target_output_name}}.{{source_name_part}}.o" ]
      description = "assemble {{source}}"
    }

    is_mac = false
    is_ios=false
    if (is_mac || is_ios) {
      not_needed([ "ar" ])  # We use libtool instead.
    }

    tool("alink") {
      if (is_mac || is_ios) {
        command = "libtool -static -o {{output}} -no_warning_for_no_symbols {{inputs}}"
      } else {
        rspfile = "{{output}}.rsp"
        rspfile_content = "{{inputs}}"
        rm_py = rebase_path("../rm.py")
        command = "python \"$rm_py\" \"{{output}}\" && $ar rcs {{output}} @$rspfile"
      }

      outputs =
          [ "{{root_out_dir}}/{{target_output_name}}{{output_extension}}" ]
      default_output_extension = ".a"
      output_prefix = "lib"
      description = "link {{output}}"
    }

    tool("solink") {
      soname = "{{target_output_name}}{{output_extension}}"

      rpath = "-Wl,-soname,$soname"
      if (is_mac || is_ios) {
        rpath = "-Wl,-install_name,@rpath/$soname"
      }

      rspfile = "{{output}}.rsp"
      rspfile_content = "{{inputs}}"

      # --start-group/--end-group let us link multiple .a {{inputs}}
      # without worrying about their relative order on the link line.
      #
      # This is mostly important for traditional linkers like GNU ld and Gold.
      # The Mac/iOS linker neither needs nor accepts these flags.
      # LLD doesn't need these flags, but accepts and ignores them.
      _start_group = "-Wl,--start-group"
      _end_group = "-Wl,--end-group"
      if (is_mac || is_ios) {
        _start_group = ""
        _end_group = ""
      }

      command = "$link -shared {{ldflags}} $_start_group @$rspfile {{frameworks}} {{solibs}} $_end_group {{libs}} $rpath -o {{output}}"
      outputs = [ "{{root_out_dir}}/$soname" ]
      output_prefix = "lib"
      default_output_extension = ".so"
      description = "link {{output}}"
    }

    tool("link") {
      exe_name = "{{root_out_dir}}/{{target_output_name}}{{output_extension}}"
      rspfile = "$exe_name.rsp"
      rspfile_content = "{{inputs}}"


  
      command = "$link {{ldflags}} @$rspfile {{frameworks}} {{solibs}} {{libs}} -o $exe_name"

      outputs = [ "$exe_name" ]
      description = "link {{output}}"
    }

    stamp = "touch"

    tool("stamp") {
      command = "$stamp {{output}}"
      description = "stamp {{output}}"
    }

    tool("copy") {
      cp_py = rebase_path("../cp.py")
      command = "python \"$cp_py\" {{source}} {{output}}"
      description = "copy {{source}} {{output}}"
    }

    tool("copy_bundle_data") {
      cp_py = rebase_path("../cp.py")
      command = "python \"$cp_py\" {{source}} {{output}}"
      description = "copy_bundle_data {{source}} {{output}}"
    }

    # We don't currently have any xcasset files so make this a NOP
    tool("compile_xcassets") {
      command = "true"
      description = "compile_xcassets {{output}}"
    }

    toolchain_args = {
      current_cpu = invoker.cpu
      current_os = invoker.os
    }
  }
}

gcc_like_toolchain("gcc_like") {

  ar = "ar"
  cc = "cc"
  cxx = "c++"
  target_ar = ar
  target_cc = cc
  target_cxx = cxx
  target_link = target_cxx
  cpu = current_cpu
  os = current_os
  ar = target_ar
  cc = target_cc
  cxx = target_cxx
  link = target_link
}

```
即一般采用template来复用toolchain的配置，从而可以减少维护不同平台、配置下的构建工具链的配置成本。上面是一个并不完美的配置。拷贝时请慎重。准备就绪后，只需要`gn gen`，即可得到`ninja`的构建文件，然后使用`ninja`来构建目标即可。

## 阅读更多

- [makefile tutorial](https://seisman.github.io/how-to-write-makefile/introduction.html)
- [cmake tutorial](https://cmake.org/cmake/help/latest/guide/tutorial/index.html)
- [cmake index](https://cmake.org/cmake/help/latest/genindex.html)
