package com.example.tester

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.FileUtils
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.ImageView
import com.example.tester.ml.ProductModel
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.*

class MainActivity : AppCompatActivity() {

    lateinit var textureView: TextureView
    lateinit var imageView: ImageView

    lateinit var bmp: Bitmap
    lateinit var imgProcessor: ImageProcessor


    val paint = Paint()
    var colors = listOf<Int>(
    Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
    Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)

    lateinit var labels:List<String>
    lateinit var model:ProductModel

    lateinit var cameraManager: CameraManager
    lateinit var cameraDevice: CameraDevice

    lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        get_permission();
        model = ProductModel.newInstance(this)

        labels = FileUtil.loadLabels(this,"product_labels.txt")

        imgProcessor = ImageProcessor.Builder().add(ResizeOp(6400,640, ResizeOp.ResizeMethod.BILINEAR)).build()

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{

            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
                Log.d("Module Opened:","Camera has been opened")
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bmp = textureView.bitmap!!

                // Creates inputs for reference.
                var image = TensorImage.fromBitmap(bmp)
                image = imgProcessor.process(image)

                // Runs model inference and gets result.
                val outputs = model.process(image)
                val output = outputs.outputAsCategoryList

                var mutable = bmp.copy(Bitmap.Config.ARGB_8888,true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width

                // Set up paint for drawing rectangle and text
                val paint = Paint().apply {
                    strokeWidth = h / 85f
                    textSize = h / 15f
                }

                output.forEachIndexed { index, category ->
                    val fl = category.score
                    if (fl > 0.5) {
                        paint.color = Color.RED // Replace with your color logic
                        paint.style = Paint.Style.STROKE
                        // Draw a fixed bounding box around the detected object
                        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                        canvas.drawRect(rect, paint)
                        paint.style = Paint.Style.FILL
                        paint.textSize = h / 15f // Adjusted based on the height
                        val label = "${category.label} ${fl}"
                        canvas.drawText(label, 0f, 0f, paint)
                    }

                }

                imageView.setImageBitmap(mutable)

            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    fun get_permission(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),101)
        }

        Log.d("Permissions Opened:","Camera has been allowed")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface),object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                onDestroy()
            }
        }, handler )
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        Log.d("Module Closed","Model closed out")
    }
}