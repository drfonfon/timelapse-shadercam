package com.androidexperiments.shadercam.example
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidexperiments.shadercam.OnRendererReadyListener
import com.androidexperiments.shadercam.egl.GlPreviewRenderer
import kotlinx.android.synthetic.main.activity_rsv.btn_record
import kotlinx.android.synthetic.main.activity_rsv.surface_view
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

class SimpleRSVShaderActivity : AppCompatActivity(), OnRendererReadyListener {

    private fun newVideoFile(): File = File(
        Environment.getExternalStorageDirectory(),
        System.currentTimeMillis().toString() + "_test_video.mp4"
    )

    private var currentFile: File = newVideoFile()

    private var isRecording = false
    private val videoRenderer: GlPreviewRenderer by lazy {
        GlPreviewRenderer(
            CanvasGlOverlayFilter()
        )
    }
    private val cameraManager: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
//    private val windowSize: Point by lazy {
//        val size = Point()
//        windowManager.defaultDisplay.getRealSize(size)
//        size
//    }
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics("0")
    }
    private val preViewSize by lazy { getOptimalPreviewSize() }

    private lateinit var mCameraDevice: CameraDevice
    private lateinit var mPreviewSession: CameraCaptureSession
    private var surfaceTexture: SurfaceTexture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rsv)

        goFullscreen(this.window)
        btn_record.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        
        videoRenderer.surfaceCreateListener = this
        surface_view.rendererCallbacks = videoRenderer

        surface_view.resume()
        try {
            surface_view.initRecorder(
                currentFile,
                720,
                1280
            )
            surface_view.release()
        } catch (ioex: IOException) {
            ioex.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mPreviewSession.stopRepeating()
            mCameraDevice.close()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } catch (acex: CameraAccessException) {
        }

        surfaceTexture?.release()
        surface_view.rendererCallbacks = null
        surface_view.pause()
        if(!isRecording) {
            currentFile.delete()
        }
    }

    private fun startRecording() {
        try {
            surface_view.initRecorder(
                currentFile,
                720,
                1280
            )
        } catch (ioex: IOException) {
            ioex.printStackTrace()
        }
        surface_view.startRecording()
        isRecording = true
        btn_record.text = "Stop"
    }

    private fun stopRecording() {
        btn_record.text = "Record"
        surface_view.stopRecording()
        currentFile = newVideoFile()
        isRecording = false
        Toast.makeText(this, "File recording complete", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("Recycle")
    private fun startPreview() = lifecycleScope.launch {
        surfaceTexture =
            videoRenderer.previewTexture?.surfaceTexture

        surfaceTexture?.setDefaultBufferSize(preViewSize!!.width, preViewSize!!.height)
        videoRenderer.cameraResolution = preViewSize!!
        videoRenderer.setAngle(0)
        videoRenderer.onStartPreview(
            surface_view,
            preViewSize!!.width.toFloat(),
            preViewSize!!.height.toFloat()
        )

        val previewSurface = Surface(surfaceTexture)
        mPreviewSession = createCaptureSession(listOf(previewSurface), null)
        mPreviewSession.setRepeatingRequest(
            getPreviewRequest(previewSurface),
            null,
            null
        )
    }

    private fun getOptimalPreviewSize(): Size? {
        val sizes = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(SurfaceTexture::class.java)

        val ASPECT_TOLERANCE = 0.001
        val targetRatio = surface_view.width.toDouble() / surface_view.height
        val allSizes = listOf(*sizes)
        allSizes.sortedWith { lhs, rhs ->
            java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE
        for (size in allSizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue
            }
            if (abs(size.width - surface_view.height) < minDiff) {
                optimalSize = size
                minDiff = abs(size.width - surface_view.height).toDouble()
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            for (size in allSizes) {
                if (abs(size.width - surface_view.height) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.width - surface_view.height).toDouble()
                }
            }
        }
        return optimalSize
    }

    override fun onRendererReady() {
        lifecycleScope.launch {
            mCameraDevice = openCameraDevice()
            startPreview()
        }
    }

    fun goFullscreen(window: Window) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // --------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun openCameraDevice(): CameraDevice {
        return suspendCancellableCoroutine { cont ->
            cameraManager.openCamera("0", object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) = cont.resume(device)

                override fun onDisconnected(device: CameraDevice) = Unit

                override fun onError(device: CameraDevice, error: Int) {
                    if (cont.isActive) cont.resumeWithException(Throwable("err $error"))
                }
            }, null)
        }
    }

    private suspend fun createCaptureSession(
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        mCameraDevice.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                cont.resumeWithException(
                    RuntimeException("Camera session configuration failed")
                )
            }
        }, handler)
    }

    private fun getPreviewRequest(surface: Surface): CaptureRequest {
        val builder = mPreviewSession.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        return builder.build()
    }
}