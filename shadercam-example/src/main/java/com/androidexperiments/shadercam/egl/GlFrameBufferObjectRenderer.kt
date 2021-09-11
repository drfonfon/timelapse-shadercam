package com.androidexperiments.shadercam.egl

import android.opengl.GLES20
import com.androidexperiments.shadercam.RendererCallbacks
import com.androidexperiments.shadercam.egl.filter.GlFilter
import java.util.LinkedList
import java.util.Queue

abstract class GlFrameBufferObjectRenderer : RendererCallbacks {

    private var framebufferObject: GlFramebufferObject? = null
    private var normalShader: GlFilter? = null

    private val runOnDraw: Queue<Runnable> = LinkedList()

    override fun onSurfaceCreated() {
        framebufferObject = GlFramebufferObject()
        normalShader = GlFilter()
        normalShader?.setup()
        onSCreated()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        framebufferObject?.setup(width, height)
        normalShader?.setFrameSize(width, height)
        onSChanged(width, height)
        GLES20.glViewport(0, 0, framebufferObject!!.width, framebufferObject!!.height)
    }

    override fun onDrawFrame() {
        synchronized(runOnDraw) {
            while (!runOnDraw.isEmpty()) {
                runOnDraw.poll().run()
            }
        }
        framebufferObject?.enable()
        framebufferObject?.let(::onDrawFrame)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        framebufferObject?.texName?.let { normalShader?.draw(it) }
    }

    abstract fun onSCreated()
    abstract fun onSChanged(width: Int, height: Int)
    abstract fun onDrawFrame(fbo: GlFramebufferObject)
}