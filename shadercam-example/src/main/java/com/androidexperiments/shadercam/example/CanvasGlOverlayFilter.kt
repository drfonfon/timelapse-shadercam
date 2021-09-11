package com.androidexperiments.shadercam.example

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.icu.text.DateFormat
import com.androidexperiments.shadercam.egl.filter.GlOverlayFilter
import java.util.Date

class CanvasGlOverlayFilter: GlOverlayFilter() {

    private val paint = Paint().apply {
        textSize = 64f
        color = Color.RED
    }

    override fun drawCanvas(canvas: Canvas) {
        canvas.drawText(DateFormat.getTimeInstance().format(Date()), 30f, 80f, paint)
    }
}