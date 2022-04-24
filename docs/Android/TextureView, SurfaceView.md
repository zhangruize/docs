## 概述

`TextureView`和`SurfaceView`主要用于提供以更低级的`OpenGLES`方式进行渲染。当然，有时候对`OpenGLES`的调用并不完全处在`Java`侧，更常见是在`jni`的引入了`ndk`的`native`侧。而`TextureView`和`SurfaceView`主要区别在于在View tree中的绘制差异。它们均可以在UI线程、或独立的渲染线程通过`OpenGLES`更新绘制。

## TextureView

`TextureView`对SurfaceTexture的绘制是会被添加在View tree中所渲染的。UI线程、独立渲染线程都可以通过`OpenGLES`更新绘制。此外此方式只能在硬件加速模式下的window适用。

## SurfaceView

`SurfaceView`对SurfaceTexture的绘制一般是脱离View tree的，默认的`SurfaceView`的`onDraw`里，`canvas`会在此view的区域挖空掉。不做绘制。此外，`SurfaceView`会对应一个独立的Surface，和`ViewRootImpl`所持有的各自独立。由此，它一般用作独立线程进行渲染。

一般层级上和`ViewRootImpl`的关系尽可能简单。要么在其上（通过`setZOrderTop(true)`，要么在其下（默认）。这也是为何`SurfaceView`在`onDraw`中对View tree元素上的绘制操作是挖洞。这意味着默认时它处于`ViewRootImpl`的Surface之下，但此区域可以被正确露出来所看到。但如果设置了背景色`setBackgroundColor`或前景、或重写了`onDraw`，则可能会使得`SurfaceView`的区域被覆盖无法可见。

而如果`setZOrderTop(true)`后，它的区域会覆盖`ViewRootImpl`，不过也可以通过设置`EGLConfig`，来让此window是半透明的（带透明维度）。从而也可以让没有绘制的区域露出下面的`ViewRootImpl`。

当引入了不止一个线程来渲染页面时，页面往往会面临不少线程同步的挑战。对于SurfaceView尤其如此。渲染线程之间无法保证同步几乎是无比常见的情况。目前也无合理的手段阻止这种问题。

## OpenGLES

Java侧操作`OpenGLES`时，也有一套常规的顺序。一般来说，需要选择`EGLDisplay`, `EGLConfig`, 从而创建`EGLContext`，然后一般需要创建`VertextShader`, `FragmentShader`，编译并链接这两类着色器、配置传入属性、定点、颜色数据缓冲。准备工作就绪后，在渲染循环中，一般会先`glClearColor`，然后更新属性，绘制定点，更新标志位`glClear`，最后提交`eglSwapBuffers`。

值得注意的是，window和eglContext的关系是可以多对多的，由此需要`eglMakeCurrent`来指定当前的渲染上下文。

为何需要上下文？因为`OpenGL`基本是面向“函数”的，这种设计模式下如果需要维护一系列环境信息，比如屏幕、纹理id、定点Buffer id、着色器id、错误信息等等，都需要关联一个Context，从而避免和同进程里的其他实例混淆。由此，一般面向”函数“会提供两种方式解决：

- 上下文实例作为入参（一般是首个参数），类似面向对象中隐式以"this"作为首参。
- 调用一个函数明确绑定当前的上下文实例。并选择一定的机制让其得到释放。

`OpenGLES`选择的是第二种方式。

## GLSurfaceView

因为上述的`OpenGLES`的操作流程比较固定，所以官方内置了一个`GLSurfaceView`其已经包含了`GLThread`和上述的大部分功能，并提供了`Renderer`接口，方便外部只需要实现`Renderer`并`setRenderer`即可在渲染方法中实现自己的渲染逻辑。

## 示例：TextureView

```kotlin
class MyTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextureView(context, attrs) {

    init {
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                RenderThread(surface).start()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                Log.d("zrz", "update")
            }

        }
    }

    class RenderThread(val surfaceTexture: SurfaceTexture) : Thread() {

        private val config = intArrayOf(
            EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 0,
            EGL_STENCIL_SIZE, 0,
            EGL_NONE
        )

        override fun run() {
            super.run()
            val egl = EGLContext.getEGL() as EGL10
            val eglDisplay = egl.eglGetDisplay(EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(eglDisplay, intArrayOf(0, 0))

            val configsCount = intArrayOf(0);
            val configs = arrayOfNulls<EGLConfig?>(1);
            egl.eglChooseConfig(eglDisplay, config, configs, 1, configsCount)
            val eglConfig = configs[0]!!

            val eglContext = egl.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL_NO_CONTEXT,
                intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE)
            )
            val startTime = System.currentTimeMillis()
            val eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null)
            while (egl.eglGetError() == EGL_SUCCESS) {
                egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                val time = (System.currentTimeMillis() - startTime) * 0.001f
                Log.d("zrz", "$time, ${sin(time + 0f)}")
                GLES20.glClearColor(
                    abs(sin(0f + time)),
                    abs(sin(1f + time)),
                    abs(sin(2f + time)),
                    1f
                )
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                egl.eglSwapBuffers(eglDisplay, eglSurface)
                sleep(10)
            }
        }
    }
}
```

## 示例：GLSurfaceView

```kotlin

class MySurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {


    init {
        setEGLContextClientVersion(2)
        setZOrderOnTop(true)
        val holder = holder
        setEGLConfigChooser( 8, 8, 8, 8, 16, 0 )
        holder.setFormat(PixelFormat.RGBA_8888)
        setRenderer(MyRendererFromLesson1())
    }
}


public class MyRendererFromLesson1 implements GLSurfaceView.Renderer {
    /**
     * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
     * of being located at the center of the universe) to world space.
     */
    private float[] mModelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
     * it positions things relative to our eye.
     */
    private float[] mViewMatrix = new float[16];

    /**
     * Store the projection matrix. This is used to project the scene onto a 2D viewport.
     */
    private float[] mProjectionMatrix = new float[16];

    /**
     * Allocate storage for the final combined matrix. This will be passed into the shader program.
     */
    private float[] mMVPMatrix = new float[16];

    /**
     * Store our model data in a float buffer.
     */
    private final FloatBuffer mTriangle1Vertices;
    private final FloatBuffer mTriangle2Vertices;
    private final FloatBuffer mTriangle3Vertices;

    /**
     * This will be used to pass in the transformation matrix.
     */
    private int mMVPMatrixHandle;

    /**
     * This will be used to pass in model position information.
     */
    private int mPositionHandle;

    /**
     * This will be used to pass in model color information.
     */
    private int mColorHandle;

    /**
     * How many bytes per float.
     */
    private final int mBytesPerFloat = 4;

    /**
     * How many elements per vertex.
     */
    private final int mStrideBytes = 7 * mBytesPerFloat;

    /**
     * Offset of the position data.
     */
    private final int mPositionOffset = 0;

    /**
     * Size of the position data in elements.
     */
    private final int mPositionDataSize = 3;

    /**
     * Offset of the color data.
     */
    private final int mColorOffset = 3;

    /**
     * Size of the color data in elements.
     */
    private final int mColorDataSize = 4;

    /**
     * Initialize the model data.
     */
    public MyRendererFromLesson1() {
        // Define points for equilateral triangles.

        // This triangle is red, green, and blue.
        final float[] triangle1VerticesData = {
                // X, Y, Z,
                // R, G, B, A
                -0.5f, -0.25f, 0.0f,
                1.0f, 0.0f, 0.0f, 1.0f,

                0.5f, -0.25f, 0.0f,
                0.0f, 0.0f, 1.0f, 1.0f,

                0.0f, 0.559016994f, 0.0f,
                0.0f, 1.0f, 0.0f, 1.0f};

        // This triangle is yellow, cyan, and magenta.
        final float[] triangle2VerticesData = {
                // X, Y, Z,
                // R, G, B, A
                -0.5f, -0.25f, 0.0f,
                1.0f, 1.0f, 0.0f, 1.0f,

                0.5f, -0.25f, 0.0f,
                0.0f, 1.0f, 1.0f, 1.0f,

                0.0f, 0.559016994f, 0.0f,
                1.0f, 0.0f, 1.0f, 1.0f};

        // This triangle is white, gray, and black.
        final float[] triangle3VerticesData = {
                // X, Y, Z,
                // R, G, B, A
                -0.5f, -0.25f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f,

                0.5f, -0.25f, 0.0f,
                0.5f, 0.5f, 0.5f, 1.0f,

                0.0f, 0.559016994f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f};

        // Initialize the buffers.
        mTriangle1Vertices = ByteBuffer.allocateDirect(triangle1VerticesData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangle2Vertices = ByteBuffer.allocateDirect(triangle2VerticesData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangle3Vertices = ByteBuffer.allocateDirect(triangle3VerticesData.length * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        mTriangle1Vertices.put(triangle1VerticesData).position(0);
        mTriangle2Vertices.put(triangle2VerticesData).position(0);
        mTriangle3Vertices.put(triangle3VerticesData).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Set the background clear color to gray.
//        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 0.5f);

        // Position the eye behind the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = 1.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        final String vertexShader =
                "uniform mat4 u_MVPMatrix;      \n"        // A constant representing the combined model/view/projection matrix.

                        + "attribute vec4 a_Position;     \n"        // Per-vertex position information we will pass in.
                        + "attribute vec4 a_Color;        \n"        // Per-vertex color information we will pass in.

                        + "varying vec4 v_Color;          \n"        // This will be passed into the fragment shader.

                        + "void main()                    \n"        // The entry point for our vertex shader.
                        + "{                              \n"
                        + "   v_Color = a_Color;          \n"        // Pass the color through to the fragment shader.
                        // It will be interpolated across the triangle.
                        + "   gl_Position =    \n"    // gl_Position is a special variable used to store the final position.
                        + "               a_Position;   \n"     // Multiply the vertex by the matrix to get the final point in
                        + "}                              \n";    // normalized screen coordinates.

        final String fragmentShader =
                "precision mediump float;       \n"        // Set the default precision to medium. We don't need as high of a
                        // precision in the fragment shader.
                        + "varying vec4 v_Color;          \n"        // This is the color from the vertex shader interpolated across the
                        // triangle per fragment.
                        + "void main()                    \n"        // The entry point for our fragment shader.
                        + "{                              \n"
                        + "   gl_FragColor = v_Color;     \n"        // Pass the color directly through the pipeline.
                        + "}                              \n";

        // Load in the vertex shader.
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);

        if (vertexShaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(vertexShaderHandle, vertexShader);

            // Compile the shader.
            GLES20.glCompileShader(vertexShaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(vertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(vertexShaderHandle);
                vertexShaderHandle = 0;
            }
        }

        if (vertexShaderHandle == 0) {
            throw new RuntimeException("Error creating vertex shader.");
        }

        // Load in the fragment shader shader.
        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);

        if (fragmentShaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);

            // Compile the shader.
            GLES20.glCompileShader(fragmentShaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(fragmentShaderHandle);
                fragmentShaderHandle = 0;
            }
        }

        if (fragmentShaderHandle == 0) {
            throw new RuntimeException("Error creating fragment shader.");
        }

        // Create a program object and store the handle to it.
        int programHandle = GLES20.glCreateProgram();

        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle);
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            GLES20.glBindAttribLocation(programHandle, 0, "a_Position");
            GLES20.glBindAttribLocation(programHandle, 1, "a_Color");
            GLES20.glLinkProgram(programHandle);

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        // Set program handles. These will later be used to pass in values to the program.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(programHandle, "a_Color");

        // Tell OpenGL to use this program when rendering.
        GLES20.glUseProgram(programHandle);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 10.0f;

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        // Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);

        // Draw the triangle facing straight on.
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);
        drawTriangle(mTriangle1Vertices);

        // Draw one translated a bit down and rotated to be flat on the ground.
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, -1.0f, 0.0f);
        Matrix.rotateM(mModelMatrix, 0, 90.0f, 1.0f, 0.0f, 0.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);
        drawTriangle(mTriangle2Vertices);

        // Draw one translated a bit to the right and rotated to be facing to the left.
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 1.0f, 0.0f, 0.0f);
        Matrix.rotateM(mModelMatrix, 0, 90.0f, 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);
        drawTriangle(mTriangle3Vertices);
    }

    /**
     * Draws a triangle from the given vertex data.
     *
     * @param aTriangleBuffer The buffer containing the vertex data.
     */
    private void drawTriangle(final FloatBuffer aTriangleBuffer) {
        // Pass in the position information
        aTriangleBuffer.position(mPositionOffset);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,
                mStrideBytes, aTriangleBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the color information
        aTriangleBuffer.position(mColorOffset);
        GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,
                mStrideBytes, aTriangleBuffer);

        GLES20.glEnableVertexAttribArray(mColorHandle);

        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
    }
}
```