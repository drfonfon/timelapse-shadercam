package com.androidexperiments.shadercam.egl

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.util.Size
import android.view.SurfaceView
import com.androidexperiments.shadercam.OnRendererReadyListener
import com.androidexperiments.shadercam.egl.filter.GlFilter

class GlPreviewRenderer(
    private val filter: GlFilter
) : GlFrameBufferObjectRenderer(), OnFrameAvailableListener {

    private val handler = Handler()
    var previewTexture: GlSurfaceTexture? = null
    private var texName = 0
    private val MVPMatrix = FloatArray(16)
    private val ProjMatrix = FloatArray(16)
    private val MMatrix = FloatArray(16)
    private val VMatrix = FloatArray(16)
    private val STMatrix = FloatArray(16)
    private var filterFramebufferObject: GlFramebufferObject? = null
    private var previewShader: GlPreview? = null
    private var isNewShader = true
    private var angle = 0
    private var aspectRatio = 1f
    private var scaleRatio = 1f
    private var drawScale = 1f
    private var updateTexImageCounter = 0
    private var updateTexImageCompare = 0

    var surfaceCreateListener: OnRendererReadyListener? = null
    var gestureScale = 1f
    var cameraResolution: Size? = null

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        updateTexImageCounter++
    }

    override fun onSCreated() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        val args = IntArray(1)
        GLES20.glGenTextures(args.size, args, 0)
        texName = args[0]
        previewTexture = GlSurfaceTexture(texName)
        previewTexture?.onFrameAvailableListener = this
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texName)
        EglUtil.setupSampler(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_LINEAR, GLES20.GL_NEAREST)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        filterFramebufferObject = GlFramebufferObject()
        previewShader = GlPreview(GL_TEXTURE_EXTERNAL_OES)
        previewShader?.setup()
        Matrix.setLookAtM(
            VMatrix, 0,
            0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f
        )
        isNewShader = true
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, args, 0)
        handler.post {
            surfaceCreateListener?.onRendererReady()
        }
    }

    override fun onSurfaceDestroyed() {
        filter.release()
    }

    override fun onSChanged(width: Int, height: Int) {
        filterFramebufferObject?.setup(width, height)
        previewShader?.setFrameSize(width, height)
        filter.setFrameSize(width, height)
        scaleRatio = width.toFloat() / height
        Matrix.frustumM(ProjMatrix, 0, -scaleRatio, scaleRatio, -1f, 1f, 5f, 7f)
    }

    override fun onDrawFrame(fbo: GlFramebufferObject) {
        if (drawScale != gestureScale) {
            val tempScale = 1 / drawScale
            Matrix.scaleM(MMatrix, 0, tempScale, tempScale, 1f)
            drawScale = gestureScale
            Matrix.scaleM(MMatrix, 0, drawScale, drawScale, 1f)
        }
        synchronized(this) {
            if (updateTexImageCompare != updateTexImageCounter) {
                while (updateTexImageCompare != updateTexImageCounter) {
                    previewTexture?.updateTexImage()
                    previewTexture?.getTransformMatrix(STMatrix)
                    updateTexImageCompare++
                }
            }
        }
        if (isNewShader) {
            filter.setup()
            filter.setFrameSize(fbo.width, fbo.height)
            isNewShader = false
        }
        filterFramebufferObject?.enable()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        Matrix.multiplyMM(MVPMatrix, 0, VMatrix, 0, MMatrix, 0)
        Matrix.multiplyMM(MVPMatrix, 0, ProjMatrix, 0, MVPMatrix, 0)
        previewShader?.draw(texName, MVPMatrix, STMatrix, aspectRatio)
        fbo.enable()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        filter.draw(filterFramebufferObject!!.texName)
    }

    fun setAngle(angle: Int) {
        this.angle = angle
        aspectRatio = if (angle == 90 || angle == 270) {
            cameraResolution!!.width.toFloat() / cameraResolution!!.height
        } else {
            cameraResolution!!.height.toFloat() / cameraResolution!!.width
        }
    }

    fun onStartPreview(
        view: SurfaceView,
        cameraPreviewWidth: Float,
        cameraPreviewHeight: Float
    ) {
        Matrix.setIdentityM(MMatrix, 0)
        Matrix.rotateM(MMatrix, 0, -angle.toFloat(), 0.0f, 0.0f, 1.0f)
        val viewAspect = view.measuredHeight.toFloat() / view.measuredWidth
        val cameraAspect = cameraPreviewWidth / cameraPreviewHeight
        if (viewAspect >= cameraAspect) {
            Matrix.scaleM(MMatrix, 0, 1f, 1f, 1f)
        } else {
            val adjust = cameraAspect / viewAspect
            Matrix.scaleM(MMatrix, 0, adjust, adjust, 1f)
        }
    }

}