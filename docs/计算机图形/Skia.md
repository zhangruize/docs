## 概述

Skia是一个很适合用来把玩的库。它不仅作为承上启下的关键一层（简化Cpu/Gpu/Pdf等多种方式作为渲染后端，提供了一套一致、简单的API，更专注于描述2D绘制），为大部分的UI渲染框架提供了非常重要的组成部分。

## 拓展

- Skija
- Skiko
- React Native Skia

## 示例：CPU绘制，导出图片

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

## 示例：GPU绘制，导出图片
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
  GrDirectContext *context =
      GrDirectContext::MakeGL(GrGLMakeNativeInterface()).release();
  SkSurface *surface =
      SkSurface::MakeRenderTarget(context, SkBudgeted::kYes,
                                  SkImageInfo::MakeN32Premul(300, 300))
          .release();
  if (surface == nullptr)
    abort();

  SkCanvas *canvas = surface->getCanvas();
  ...同上
}
```
## 示例：结合glfw

```cpp
int main() {
  GLFWwindow *window;
  if (!glfwInit())
    return -1;

  int w = 640;
  int h = 480;
  window = glfwCreateWindow(640, 480, "Hello World", NULL, NULL);
  glfwMakeContextCurrent(window);

  GrDirectContext *context =
      GrDirectContext::MakeGL(GrGLMakeNativeInterface()).release();

  GrGLFramebufferInfo framebufferInfo;
  framebufferInfo.fFBOID = 0;
  framebufferInfo.fFormat = GL_RGBA8;
  SkColorType colorType = kRGBA_8888_SkColorType;
  GrBackendRenderTarget backendRenderTarget(w, h, 0, 0, framebufferInfo);
  SkSurface *surface =
      SkSurface::MakeFromBackendRenderTarget(context, backendRenderTarget,
                                             kBottomLeft_GrSurfaceOrigin,
                                             colorType, nullptr, nullptr)
          .release();

  if (surface == nullptr)
    abort();

  SkCanvas *canvas = surface->getCanvas();

  glfwSwapInterval(1);

  while (!glfwWindowShouldClose(window)) {
    glfwWaitEvents();
    SkPaint paint;
    paint.setColor(SK_ColorWHITE);
    canvas->drawPaint(paint);
    paint.setColor(SK_ColorBLUE);
    canvas->drawRect({100, 200, 300, 500}, paint);
    context->flush();

    glfwSwapBuffers(window);
  }

  glfwTerminate();
  return 0;
}
```