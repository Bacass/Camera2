package com.lee.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    val TAG = "Lee"

    companion object {
        private var ORIENTATIONS = SparseIntArray()
        val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSessions: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null

    private var file: File? = null
    private val REQUEST_CAMERA_PERMISSION = 200
    private var mFlashSupported: Boolean = false
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView?.surfaceTextureListener = textureListener

        btnCapture?.setOnClickListener {
            takePicture()
        }

    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            Log.e(TAG, "isAvailable");
            openCamera()
        } else {
            Log.e(TAG, "Not Available");
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread?.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        Log.e(TAG, "openCamera() In")
        var manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            cameraId = manager.cameraIdList[0]
            var characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraId)
            var map: StreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            // Check Permission if Run higher API 23
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CAMERA_PERMISSION)
                return
            }
            Log.e(TAG, "cameraId : $cameraId")
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreview() {
        var texture = textureView.surfaceTexture
        texture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
        var surface = Surface(texture)
        captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder?.addTarget(surface)
        cameraDevice?.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Toast.makeText(this@MainActivity, "Changed", Toast.LENGTH_SHORT).show()
            }

            override fun onConfigured(session: CameraCaptureSession) {
                if (cameraDevice == null)
                    return
                cameraCaptureSessions = session
                updatePreview()
            }

        }, null)
    }

    private fun updatePreview() {
        if (cameraDevice == null)
            Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
        captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions?.setRepeatingRequest(captureRequestBuilder?.build(), null, mBackgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun takePicture() {
        if (cameraDevice == null)
            return

        var manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraDevice?.id)
            var jpegSize: Array<Size>? = null

            if (characteristics != null) {
                jpegSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG)
            }

            //Capture image with custom size
            val display = windowManager.defaultDisplay
            val metricsB = DisplayMetrics()
            display.getMetrics(metricsB)
            val size = getPoint(display)
            var width = size.x
            var height = size.y

            if (jpegSize != null && jpegSize.size > 0) {
                width = jpegSize[0].width
                height = jpegSize[0].height
            }

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurface = mutableListOf<Surface>()
            outputSurface.add(reader.surface)
            outputSurface.add(Surface(textureView.surfaceTexture))

            var captureBuilder: CaptureRequest.Builder? =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(reader.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)


            var rotation: Int = windowManager.defaultDisplay.rotation
            captureBuilder?.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            file = File("${Environment.getExternalStorageDirectory()}/" + UUID.randomUUID().toString())

            var readerListener: ImageReader.OnImageAvailableListener? = ImageReader.OnImageAvailableListener {
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    var buffer = image!!.planes[0].buffer
                    var bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)

                    save(bytes)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image?.close()
                }
            }

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Toast.makeText(this@MainActivity, "Saved " + file, Toast.LENGTH_SHORT).show()
                    createCameraPreview()

                }
            }

            cameraDevice?.createCaptureSession(outputSurface, object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        cameraCaptureSessions?.capture(captureBuilder?.build(), captureListener, mBackgroundHandler)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            }, mBackgroundHandler)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun save(bytes: ByteArray) {
        var outputStream: OutputStream? = null
        try {
            outputStream = FileOutputStream(file)
            outputStream.write(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream?.close()
        }
    }

    fun getPoint(display: Display): Point {
        val point = Point()
        display.getSize(point)
        return point
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "check camera permission plz...", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    var textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.e(TAG, "onSurfaceTextureAvailable()")
            openCamera()
        }

    }

    var stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.e(TAG, "onOpened() In")
            cameraDevice = camera
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e(TAG, "onDisconnected() In")
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "onError() In")
            cameraDevice?.close()
            cameraDevice = null
        }

    }
}
