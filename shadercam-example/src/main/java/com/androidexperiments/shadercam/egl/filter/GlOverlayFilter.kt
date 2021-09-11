package com.androidexperiments.shadercam.egl.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Size
import java.util.Date

private const val FRAGMENT_SHADER = "precision mediump float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform lowp sampler2D sTexture;\n" +
        "uniform lowp sampler2D oTexture;\n" +
        "void main() {\n" +
        "   lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);\n" +
        "   lowp vec4 textureColor2 = texture2D(oTexture, vTextureCoord);\n" +
        "   \n" +
        "   gl_FragColor = mix(textureColor, textureColor2, textureColor2.a);\n" +
        "}\n"

abstract class GlOverlayFilter : GlFilter(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER) {

    private val textures = IntArray(1)

    private var bitmap: Bitmap? = null
    private var inputResolution = Size(1280, 720)

    override fun setFrameSize(width: Int, height: Int) {
        super.setFrameSize(width, height)
        inputResolution = Size(width, height)
    }

    override fun setup() {
        super.setup() // 1
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        createBitmap()
    }

    public override fun onDraw() {
        val isNull = bitmap == null
        val newWidth = bitmap?.width != inputResolution.width
        val newHeight = bitmap?.height != inputResolution.height
        if (isNull || newWidth || newHeight) {
            createBitmap()
        }
        bitmap?.eraseColor(Color.argb(0, 0, 0, 0))
        val bitmapCanvas = Canvas(bitmap!!)
        bitmapCanvas.scale(
            1f,
            -1f,
            (bitmapCanvas.width / 2).toFloat(),
            (bitmapCanvas.height / 2).toFloat()
        )
        drawCanvas(bitmapCanvas)
        val offsetDepthMapTextureUniform = getHandle("oTexture") // 3
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        if (bitmap != null && !bitmap!!.isRecycled) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, 0)
        }
        GLES20.glUniform1i(offsetDepthMapTextureUniform, 3)
    }

    abstract fun drawCanvas(canvas: Canvas)

    private fun createBitmap() {
        if (bitmap?.isRecycled != true) {
            bitmap?.recycle()
        }
        bitmap = Bitmap.createBitmap(
            inputResolution.width,
            inputResolution.height,
            Bitmap.Config.ARGB_8888
        )
    }

}