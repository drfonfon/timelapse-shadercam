package com.androidexperiments.shadercam.egl

import android.opengl.GLES20
import android.opengl.GLException
import android.util.Log
import com.androidexperiments.shadercam.egl.filter.GlFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object EglUtil {

    private const val FLOAT_SIZE_BYTES = 4
    private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

    fun createFragmentShaderSourceOESIfNeed(texTarget: Int): String {
        return if (texTarget == GL_TEXTURE_EXTERNAL_OES) {
            StringBuilder()
                .append("#extension GL_OES_EGL_image_external : require\n")
                .append(GlFilter.DEFAULT_FRAGMENT_SHADER.replace("sampler2D", "samplerExternalOES"))
                .toString()
        } else GlFilter.DEFAULT_FRAGMENT_SHADER
    }

    fun loadShader(strSource: String?, iType: Int): Int {
        val compiled = IntArray(1)
        val iShader = GLES20.glCreateShader(iType)
        GLES20.glShaderSource(iShader, strSource)
        GLES20.glCompileShader(iShader)
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.d(
                "Load Shader Failed", """
     Compilation
     ${GLES20.glGetShaderInfoLog(iShader)}
     """.trimIndent()
            )
            return 0
        }
        return iShader
    }

    @Throws(GLException::class)
    fun createProgram(vertexShader: Int, pixelShader: Int): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, pixelShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Could not link program")
        }
        return program
    }

    fun setupSampler(target: Int, mag: Int, min: Int) {
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, mag.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, min.toFloat())
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun createBuffer(data: FloatArray): Int {
        return createBuffer(toFloatBuffer(data))
    }

    private fun createBuffer(data: FloatBuffer): Int {
        val buffers = IntArray(1)
        GLES20.glGenBuffers(buffers.size, buffers, 0)
        updateBufferData(buffers[0], data)
        return buffers[0]
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer
            .allocateDirect(data.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data).position(0)
        return buffer
    }

    private fun updateBufferData(bufferName: Int, data: FloatBuffer) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferName)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            data.capacity() * FLOAT_SIZE_BYTES,
            data,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}