package com.androidexperiments.shadercam.egl

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener

class GlSurfaceTexture(texName: Int) : OnFrameAvailableListener {

    val surfaceTexture: SurfaceTexture = SurfaceTexture(texName)

    var onFrameAvailableListener: OnFrameAvailableListener? = null

    init {
        surfaceTexture.setOnFrameAvailableListener(this)
    }

    fun updateTexImage() {
        surfaceTexture.updateTexImage()
    }

    fun getTransformMatrix(mtx: FloatArray?) {
        surfaceTexture.getTransformMatrix(mtx)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        onFrameAvailableListener?.onFrameAvailable(this.surfaceTexture)
    }

    fun release() {
        surfaceTexture.release()
    }
}