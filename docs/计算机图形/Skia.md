## 概述

Skia是一个很适合用来把玩的库。它不仅作为承上启下的关键一层（简化Cpu/Gpu/Pdf等多种方式作为渲染后端，提供了一套一致、简单的API，更专注于描述2D绘制），为大部分的UI渲染框架提供了非常重要的组成部分。

## 拓展

- Skija
- Skiko
- React Native Skia

## 示例：

光栅化画布，绘制后导出为png。

```cpp
#include "include/core/SkCanvas.h"
#include "include/core/SkColor.h"
#include "include/core/SkRect.h"
#include "include/core/SkSurface.h"

void draw(SkCanvas *canvas) {
  canvas->save();
  canvas->translate(SkIntToScalar(128), SkIntToScalar(128));
  canvas->rotate(SkIntToScalar(45));
  SkRect rect = SkRect::MakeXYWH(-90.5f, -90.5f, 181.0f, 181.0f);
  SkPaint paint;
  paint.setColor(SK_ColorBLUE);
  canvas->drawRect(rect, paint);
  canvas->restore();
}

int main() {
  sk_sp<SkSurface> rasterSurface = SkSurface::MakeRasterN32Premul(600, 400);
  SkCanvas *rasterCanvas = rasterSurface->getCanvas();

  draw(rasterCanvas);

  sk_sp<SkImage> img(rasterSurface->makeImageSnapshot());
  if (!img) {
    return -1;
  }
  sk_sp<SkData> png(img->encodeToData());
  if (!png) {
    return -1;
  }
  SkFILEWStream out("test.png");
  (void)out.write(png->data(), png->size());
  return 0;
}
```

cmakelists.txt，`target_include_directories`和`find_library`根据实际skia的`include`目录和构建产物的目录指定。在mac上构建时，需要引入mac下的framework。详见`target_link_libraries`。

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
