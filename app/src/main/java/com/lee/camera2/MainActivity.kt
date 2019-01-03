package com.lee.camera2

import android.content.Context
import android.databinding.DataBindingUtil
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.lee.camera2.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*


class MainActivity : AppCompatActivity() {

    var mBinding: ActivityMainBinding? = null

    private var ORIENTATIONS = SparseIntArray()

    companion object {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private var cameraId: String? = null
    private val cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
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

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        mBinding?.textureView?.setSurfaceTextureListener(object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return false
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                openCamera()
            }
        })

        mBinding?.btnCapture?.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                takePicture()
            }
        })

    }


    private fun openCamera() {

    }

    private fun takePicture() {
        if (cameraDevice == null)
            return

        var manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraDevice.id)
            var jpegSize: Array<Size>? = null

            if (characteristics != null) {
                jpegSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG)
            }

            var width = 640
            var height = 480

            if (jpegSize != null && jpegSize.size > 0) {
                width = jpegSize[0].width
                height = jpegSize[0].height
            }

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurface = mutableListOf<Surface>()
            outputSurface.add(reader.surface)
            outputSurface.add(Surface(textureView.surfaceTexture))

            var captureBuilder: CaptureRequest.Builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)


            var rotation: Int = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            file = File("${Environment.getExternalStorageDirectory()}/" + UUID.randomUUID().toString())
//            var readerListener = ImageReader.OnImageAvailableListener(object: ImageReader.OnImageAvailableListener{
//                override fun onImageAvailable(reader: ImageReader?) {
//                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//                }
//
//            })

            var readerListener: ImageReader.OnImageAvailableListener? = ImageReader.OnImageAvailableListener {
                try {
                    var image: Image? = null

//                    image = reader.acquireLatestImage()
//                    var buffer: ByteBuffer? = image.planes[0].buffer
//                    var bytes: Array<Byte> = Array<Byte>(buffer!!.capacity())
//                    buffer.get(bytes)


                    image = reader.acquireLatestImage()
                    val buffer = image!!.planes[0].buffer
                    val bytes = arrayOfNulls<Byte>(buffer.capacity())
                    buffer.get(bytes!!)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
