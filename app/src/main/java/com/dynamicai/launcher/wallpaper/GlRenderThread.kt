package com.dynamicai.launcher.wallpaper

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GlRenderThread(
    private val surface: Surface,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) {

    @Volatile private var running = false
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var textureId = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTextureLoc = 0
    private var uScaleLoc = 0

    private lateinit var surfaceTexture: SurfaceTexture
    lateinit var videoSurface: Surface
        private set

    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0

    var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    fun start() {
        thread = HandlerThread("GlRenderThread")
        thread.start()
        handler = Handler(thread.looper)
        handler.post { doStart() }
    }

    fun stop() {
        if (!::handler.isInitialized) return
        VideoWallpaperService.log("GL stop()")
        running = false
        handler.post { doStop() }
        thread.quitSafely()
    }

    fun pause() {
        if (!::handler.isInitialized) return
        running = false
    }

    fun resume() {
        if (!::handler.isInitialized) return
        if (running) return
        running = true
        handler.post(renderLoop)
    }

    private val renderLoop = object : Runnable {
        override fun run() {
            if (!running) return
            drawFrame()
            handler.postDelayed(this, frameDelayMs)
        }
    }

    @Volatile var frameDelayMs: Long = 16

    fun setFps(fps: Int) {
        frameDelayMs = (1000L / fps.coerceIn(1, 60))
    }

    fun updateVideoSize(w: Int, h: Int) {
        videoWidth = w
        videoHeight = h
    }

    private fun doStart() {
        try {
            setupEgl()
            setupGl()

            surfaceTexture = SurfaceTexture(textureId)
            videoSurface = Surface(surfaceTexture)
            onSurfaceTextureReady?.invoke(surfaceTexture)
            running = true

            // ==== Явний цикл рендера, 60 fps ====
            val renderRunnable = object : Runnable {
                override fun run() {
                    if (!running) return
                    drawFrame()
                    handler.postDelayed(this, 16)
                }
            }
            handler.post(renderRunnable)

            Log.d("GlRenderThread", "started, surface=${surfaceWidth}x$surfaceHeight")
        } catch (t: Throwable) {
            Log.e("GlRenderThread", "doStart failed", t)
        }
    }

    private fun doStop() {
        VideoWallpaperService.log("GL doStop()")
        running = false
        try {
            if (::surfaceTexture.isInitialized) surfaceTexture.release()
            if (::videoSurface.isInitialized) videoSurface.release()
        } catch (_: Throwable) {}
        try {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        } catch (_: Throwable) {}
        try {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        } catch (_: Throwable) {}
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attrs, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("No EGL config")

        val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)

        val surfAttrs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfAttrs, 0)

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun setupGl() {
        val vs = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform vec2 uScale;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (aTexCoord - 0.5) * uScale + 0.5;
            }
        """.trimIndent()

        val fs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vsh = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fsh = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vsh)
            GLES20.glAttachShader(it, fsh)
            GLES20.glLinkProgram(it)
        }
        GLES20.glUseProgram(program)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
        uScaleLoc = GLES20.glGetUniformLocation(program, "uScale")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val status = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            throw RuntimeException("Shader compile failed: ${GLES20.glGetShaderInfoLog(sh)}")
        }
        return sh
    }

    private fun drawFrame() {
        if (!running) return
        if (!::surfaceTexture.isInitialized) return
        try {
            try {
                surfaceTexture.updateTexImage()
            } catch (t: Throwable) {
                // ignore
            }

            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val vw = videoWidth
            val vh = videoHeight
            if (vw > 0 && vh > 0) {
                val videoAspect = vw.toFloat() / vh
                val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight

                val scaleX: Float
                val scaleY: Float
                if (videoAspect > surfaceAspect) {
                    scaleX = surfaceAspect / videoAspect
                    scaleY = 1f
                } else {
                    scaleX = 1f
                    scaleY = videoAspect / surfaceAspect
                }
                GLES20.glUniform2f(uScaleLoc, scaleX, scaleY)
            } else {
                GLES20.glUniform2f(uScaleLoc, 1f, 1f)
            }

            val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val texCoords = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, toFloatBuffer(vertices))
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, toFloatBuffer(texCoords))

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTextureLoc, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTexCoordLoc)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        } catch (t: Throwable) {
            Log.e("GlRenderThread", "drawFrame failed", t)
        }
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(arr.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(arr)
        fb.position(0)
        return fb
    }
}
