package com.androidexperiments.shadercam

interface RendererCallbacks {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun onSurfaceDestroyed()
    fun onDrawFrame()
}