package com.androidexperiments.shadercam

import android.content.Context
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class RecordableSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {

    private var mSurface: Surface? = null
    private var mWidth = 0
    private var mHeight = 0
    private var mDesiredWidth = 0
    private var mDesiredHeight = 0
    private var isPaused = false
    private var mediaRecorder: MediaRecorder? = null
    private var renderThread: ARRenderThread? = null
    private val mIsRecording = AtomicBoolean(false)
    private val mHasGLContext = AtomicBoolean(false)
    private var mRendererCallbacksWeakReference = WeakReference<RendererCallbacks>(null)
    private val mSizeChange = AtomicBoolean(false)

    var rendererCallbacks: RendererCallbacks?
        get() = mRendererCallbacksWeakReference.get()
        set(surfaceRendererCallbacks) {
            mRendererCallbacksWeakReference = WeakReference(surfaceRendererCallbacks)
        }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        if (!mHasGLContext.get()) {
            mSurface = MediaCodec.createPersistentInputSurface()
            renderThread = ARRenderThread()
        }
        this.holder.addCallback(renderThread)
        if (holder.surface.isValid) {
            renderThread?.startInNeed()
        }
        isPaused = false
    }

    @Throws(IOException::class)
    fun initRecorder(saveToFile: File?, desiredWidth: Int, desiredHeight: Int) {
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setInputSurface(mSurface!!)

        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_1080P)
        mediaRecorder.setCaptureRate(14.9)
        mediaRecorder.setProfile(profile)
        mediaRecorder.setMaxDuration(2 * 60 * 60 * 1000)
        mediaRecorder.setMaxFileSize(500_000_000)

        mDesiredHeight = desiredHeight
        mDesiredWidth = desiredWidth

        mediaRecorder.setVideoSize(mDesiredWidth, mDesiredHeight)
        saveToFile?.let { mediaRecorder.setOutputFile(saveToFile.path) }

        mediaRecorder.prepare()
        this.mediaRecorder = mediaRecorder
    }

    fun startRecording(): Boolean {
        var success = true
        try {
            mediaRecorder?.start()
            mIsRecording.set(true)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            success = false
            mIsRecording.set(false)
            mediaRecorder?.reset()
            mediaRecorder?.release()
        }
        return success
    }

    @Throws(IllegalStateException::class)
    fun stopRecording(): Boolean {
        return if (mIsRecording.get()) {
            var success = true
            try {
                mediaRecorder?.stop()
                mIsRecording.set(false)
            } catch (e: RuntimeException) {
                e.printStackTrace()
                success = false
            } finally {
                mediaRecorder?.release()
            }
            success
        } else {
            throw IllegalStateException("Cannot stop. Is not recording.")
        }
    }

    fun release() {
        mediaRecorder?.release()
    }

    private inner class ARRenderThread : Thread(), SurfaceHolder.Callback2 {

        var mEGLDisplay: EGLDisplay? = null
        var mEGLContext: EGLContext? = null
        var mEGLSurface: EGLSurface? = null
        var mEGLSurfaceMedia: EGLSurface? = null
        var config = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
        private val mLoop = AtomicBoolean(false)

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                config[10] = EGLExt.EGL_RECORDABLE_ANDROID
            }
        }

        fun startInNeed() {
            if (!this.isAlive && !this.isInterrupted && this.state != State.TERMINATED) {
                start()
            }
        }

        private fun chooseEglConfig(eglDisplay: EGLDisplay?): EGLConfig? {
            val configsCount = intArrayOf(0)
            val configs = arrayOfNulls<EGLConfig>(1)
            EGL14.eglChooseConfig(
                eglDisplay, config, 0, configs, 0, configs.size, configsCount,
                0
            )
            return configs[0]
        }

        override fun run() {
            if (mHasGLContext.get()) {
                return
            }
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)
            val eglConfig = chooseEglConfig(mEGLDisplay)
            mEGLContext = EGL14
                .eglCreateContext(
                    mEGLDisplay,
                    eglConfig,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0
                )
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            mEGLSurface = EGL14.eglCreateWindowSurface(
                mEGLDisplay, eglConfig, this@RecordableSurfaceView,
                surfaceAttribs, 0
            )
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
            mRendererCallbacksWeakReference.get()?.onSurfaceCreated()
            mEGLSurfaceMedia =
                EGL14.eglCreateWindowSurface(mEGLDisplay, eglConfig, mSurface, surfaceAttribs, 0)
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
            mHasGLContext.set(true)
            mLoop.set(true)
            while (mLoop.get()) {
                if (!isPaused) {
                    if (mSizeChange.get()) {
                        GLES20.glViewport(0, 0, mWidth, mHeight)
                        mRendererCallbacksWeakReference.get()?.onSurfaceChanged(mWidth, mHeight)
                        mSizeChange.set(false)
                    }
                    if (mEGLSurface != null && mEGLSurface !== EGL14.EGL_NO_SURFACE) {
                        mRendererCallbacksWeakReference.get()?.onDrawFrame()
                        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
                        if (mIsRecording.get()) {
                            EGL14.eglMakeCurrent(
                                mEGLDisplay,
                                mEGLSurfaceMedia,
                                mEGLSurfaceMedia,
                                mEGLContext
                            )
                            if (mRendererCallbacksWeakReference.get() != null) {
                                GLES20.glViewport(0, 0, mDesiredWidth, mDesiredHeight)
                                mRendererCallbacksWeakReference.get()?.onDrawFrame()
                                GLES20.glViewport(0, 0, mWidth, mHeight)
                            }
                            EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceMedia)
                            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
                        }
                    }
                }
                try {
                    sleep((1f / 30f * 1000f).toLong())
                } catch (intex: InterruptedException) {
                    mRendererCallbacksWeakReference.get()?.onSurfaceDestroyed()
                    if (mEGLDisplay != null) {
                        EGL14.eglMakeCurrent(
                            mEGLDisplay,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT
                        )
                        mEGLSurface?.let { EGL14.eglDestroySurface(mEGLDisplay, it) }
                        mEGLSurfaceMedia?.let { EGL14.eglDestroySurface(mEGLDisplay, it) }
                        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
                        mHasGLContext.set(false)
                        EGL14.eglReleaseThread()
                        EGL14.eglTerminate(mEGLDisplay)
                        mSurface!!.release()
                    }
                    return
                }
            }
        }

        override fun surfaceRedrawNeeded(surfaceHolder: SurfaceHolder) = Unit

        override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
            if (!this.isAlive && !this.isInterrupted && this.state != State.TERMINATED) {
                start()
            }
        }

        override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, width: Int, height: Int) {
            if (mWidth != width) {
                mWidth = width
                mSizeChange.set(true)
            }
            if (mHeight != height) {
                mHeight = height
                mSizeChange.set(true)
            }
        }

        override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
            mLoop.set(false)
            interrupt()
            holder.removeCallback(this@ARRenderThread)
        }
    }
}