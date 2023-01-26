package com.easit.aiscanner.floatingWindow

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.speech.RecognizerIntent
import android.text.Editable
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.util.Pair
import androidx.core.view.isVisible
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants.REQUEST_RECORD_AUDIO
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FloatingWindow : Service() {
    var viewRoot: View? = null
    var windowManager: WindowManager? = null
    var rootParams: WindowManager.LayoutParams? = null
    var imageView: ImageView? = null
    var close: LinearLayout? = null
    var textView: TextView? = null
    var fab: LinearLayout? = null
    var imageText: LinearLayout? = null
    var audioText: LinearLayout? = null
    //var width = 0


    //Phase 2
    private var mMediaProjection: MediaProjection? = null
    private var mStoreDir: String? = null
    private var mImageReader: ImageReader? = null
    private var mHandler: Handler? = null
    private var mDisplay: Display? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mDensity = 0
    //private var mWidth = 0
    //private var mHeight = 0
    private var mRotation = 0
    private var mOrientationChangeCallback: OrientationChangeCallback? = null

    //Phase 3
    private lateinit var floatWindowLayoutParams: WindowManager.LayoutParams
    private var layoutType: Int? = null
    private var mContext: Context? = null
    private var mScreenshotLayout: View? = null
    //private var mWindowManager: WindowManager? = null
    private var mode: String? = "0"
    private var movedFinger = false
    private var partialScreenshotHelp: TextView? = null
    private lateinit var myRect: Rect

    var cropHeight = 0
    var cropWidth = 0
    var cropTop = 0
    var cropBottom = 0
    var cropLeft = 0
    var cropRight = 0

    var mHeight = 0
    var mWidth = 0
    var mTop = 0
    var mBottom = 0
    var mLeft = 0
    var mRight = 0
    private var STORE_DIRECTORY: String? = null

    var fWidth = 0


    var latestScannedImagePath = ""
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private val ACTION_RECORD: String = "com.easit.aiscanner.finalscanner.RECORD"
    private val ACTION_SHUTDOWN: String = "com.easit.aiscanner.finalscanner.SHUTDOWN"
    val EXTRA_RESULT_CODE = "resultCode"
    val EXTRA_RESULT_INTENT = "resultIntent"
    val VIRT_DISPLAY_FLAGS =
        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    private val handlerThread =
        HandlerThread(javaClass.simpleName, Process.THREAD_PRIORITY_BACKGROUND)
    //private val windowManager: WindowManager? = null
    private  var constr: WindowManager? = null
    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var handler: Handler? = null
    private var mgr: MediaProjectionManager? = null
    private var wmgr: WindowManager? = null

    private lateinit var data: Intent
    var resultCode = 1001
    var resultData: Intent? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()

        setupFloatingButtons()
    }

    private fun setupFloatingButtons(){
        //Main Start
        val metrics = resources.displayMetrics
        fWidth = metrics.widthPixels
        if (rootParams == null) {
            val LAYOUT_FLAG: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            rootParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "com.example.floatinglayout",
                    "Floating Layout Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.lightColor = Color.BLUE
                channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                val notificationManager =
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                notificationManager.createNotificationChannel(channel)
                val builder = NotificationCompat.Builder(this, "com.example.floatinglayout")
                val notification: Notification = builder.setOngoing(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Floating Layout Service is Running")
                    .setPriority(NotificationManager.IMPORTANCE_HIGH)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
                startForeground(2, notification)
            }
            if (viewRoot == null) {
                viewRoot = LayoutInflater.from(this).inflate(R.layout.service_floating_buttons, null)
                rootParams!!.gravity = Gravity.CENTER_HORIZONTAL or Gravity.START
                rootParams!!.x = 0
                rootParams!!.y = 0
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager!!.addView(viewRoot, rootParams)
                //textView = viewRoot!!.findViewById(R.id.textView)
                //imageView = viewRoot!!.findViewById(R.id.imageView)
                close = viewRoot!!.findViewById(R.id.close)
                fab = viewRoot!!.findViewById(R.id.capture)
                imageText = viewRoot!!.findViewById(R.id.imageText)
                audioText = viewRoot!!.findViewById(R.id.audioText)

                viewRoot!!.findViewById<View>(R.id.root)
                    .setOnTouchListener(object : View.OnTouchListener {
                        private var initialX = 0
                        private var initialY = 0
                        private var initialTouchX = 0
                        private var initialTouchY = 0

                        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                            when (motionEvent.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    initialX = rootParams!!.x
                                    initialY = rootParams!!.y
                                    initialTouchX = motionEvent.rawX.toInt()
                                    initialTouchY = motionEvent.rawY.toInt()
                                    return true
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (motionEvent.rawX < fWidth / 2) {
                                        rootParams!!.x = 0
                                    } else {
                                        rootParams!!.x = fWidth
                                    }
                                    rootParams!!.y =
                                        initialY + (motionEvent.rawY - initialTouchY).toInt()
                                    windowManager!!.updateViewLayout(viewRoot, rootParams)
                                    val xDiff = (motionEvent.rawX - initialTouchX).toInt()
                                    val yDiff = (motionEvent.rawY - initialTouchY).toInt()
                                    if (xDiff < 20 && yDiff < 20) {
                                        if (textView?.visibility == View.GONE) {
                                            textView?.visibility = View.VISIBLE
                                            //close!!.visibility = View.VISIBLE
                                        } else {
                                            textView?.visibility = View.GONE
                                            //close!!.visibility = View.GONE
                                        }
                                    }
                                    return true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    rootParams!!.x =
                                        initialX + (motionEvent.rawX - initialTouchX).toInt()
                                    rootParams!!.y =
                                        initialY + (motionEvent.rawY - initialTouchY).toInt()
                                    windowManager!!.updateViewLayout(viewRoot, rootParams)
                                    return true
                                }
                            }
                            return false
                        }
                    })
                close!!.setOnClickListener { stopService() }
                fab!!.setOnClickListener {
                    if (!audioText!!.isVisible && !imageText!!.isVisible){
                        audioText!!.visibility = View.VISIBLE
                        imageText!!.visibility = View.VISIBLE
                    }else{
                        audioText!!.visibility = View.GONE
                        imageText!!.visibility = View.GONE
                    }
                }
                imageText!!.setOnClickListener {
                    //To do
                    viewRoot!!.visibility = View.GONE
                    setupScreenCropper()
                    //startProjection(resultCode, data)
                }
                audioText!!.setOnClickListener {
                    //To do
                    viewRoot!!.visibility = View.GONE
                    val intentMain = Intent(this, AudioTransparentActivity::class.java)
                    intentMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intentMain)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupScreenCropper(){
        //Mid Phase
        val metrics = applicationContext.resources.displayMetrics
        var width = metrics.widthPixels
        var height = metrics.heightPixels


        mContext = this
        //mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val c: Char = if (Utils.isAndroidO) {
            '-';
        } else {
            '#';
        }
        val layoutParams = WindowManager.LayoutParams(
            -1, -1,
            c.toInt(), 16779016, -3
        )
        if (Utils.isAndroidP) layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        mScreenshotLayout = layoutInflater.inflate(R.layout.service_selector, null) as ViewGroup
        partialScreenshotHelp =
            mScreenshotLayout!!.findViewById<View>(R.id.help) as TextView

        (mScreenshotLayout!!.findViewById<View>(R.id.global_screenshot_flash) as ImageView)
        val mScreenshotSelectorView =
            mScreenshotLayout!!.findViewById<View>(R.id.global_screenshot_selector) as ScreenshotSelectorView
        mScreenshotLayout!!.isFocusable = true
        mScreenshotSelectorView.isFocusable = true
        mScreenshotSelectorView.isFocusableInTouchMode = true
        mScreenshotLayout!!.setOnTouchListener(View.OnTouchListener { _, _ -> true })


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        //else LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_TOAST
        else layoutType = WindowManager.LayoutParams.TYPE_PHONE

        floatWindowLayoutParams = WindowManager.LayoutParams(
            (width * 1F).toInt(),
            (height * 1F).toInt(),
            layoutType!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
            /**To try as transparent later*/
        )
        floatWindowLayoutParams.gravity = Gravity.CENTER
        floatWindowLayoutParams.x = 0
        floatWindowLayoutParams.y = 0

        //mWindowManager!!.addView(mScreenshotLayout, floatWindowLayoutParams)
        windowManager!!.addView(mScreenshotLayout, floatWindowLayoutParams)

        mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        wmgr = getSystemService(WINDOW_SERVICE) as WindowManager

        val display: Display = getWindowManager()!!.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        //For Normal Mobile .
        //For Normal Mobile .
        width = size.x
        height = size.y

        if (TransparentActivity().DeX) {
            //For Dex Mode .
            width = 1920
            height = 1080
        }

        //part 2 mid
        mScreenshotSelectorView.setOnTouchListener(View.OnTouchListener { param1View, param1MotionEvent ->
            val screenshotSelectorView = param1View as ScreenshotSelectorView
            val i = param1MotionEvent.action
            if (i != 0) {
                if (i != 1) {
                    if (i != 2) return@OnTouchListener false
                    partialScreenshotHelp!!.visibility = View.GONE
                    screenshotSelectorView.updateSelection(
                        param1MotionEvent.x.toInt(),
                        param1MotionEvent.y.toInt()
                    )
                    movedFinger = true
                    return@OnTouchListener true
                }
                if (!movedFinger) return@OnTouchListener false
                screenshotSelectorView.visibility = View.GONE

                //
                setupFloatingButtons()
                startProjection(resultCode, data)

                windowManager!!.removeView(mScreenshotLayout)
                val selectionRect = screenshotSelectorView.selectionRect
                myRect = screenshotSelectorView.selectionRect!!


                if (selectionRect != null && selectionRect.width() != 0 && selectionRect.height() != 0){
                    mScreenshotLayout!!.post(Runnable {
                        cropWidth = myRect.width()
                        cropHeight = myRect.height()
                        cropTop = myRect.top
                        cropLeft = myRect.left
                        this@FloatingWindow.stopSelf()
                    })
                }
                screenshotSelectorView.stopSelection()
                return@OnTouchListener true
            }

            //TODO Test if this is the cause for double images
            screenshotSelectorView.startSelection(
                param1MotionEvent.x.toInt(),
                param1MotionEvent.y.toInt()
            )
            //startProjection(resultCode, data)
            /*
            mHandler!!.post {
            }*/

            //imageText!!.visibility = View.GONE
            //audioText!!.visibility = View.GONE
            //viewRoot!!.visibility = View.GONE
            true
        })
        mScreenshotLayout!!.post(Runnable {
            mScreenshotSelectorView.visibility = View.VISIBLE
            mScreenshotSelectorView.requestFocus()
        })
        //TODO Figure out a way to fix visibility
        //viewRoot!!.visibility = View.VISIBLE
    }

    private fun stopService() {
        try {
            stopForeground(true)
            stopSelf()
            windowManager!!.removeViewImmediate(viewRoot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmName("getWindowManager1")
    fun getWindowManager(): WindowManager? {
        return wmgr
    }


    //Phase 2
    private inner class ImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            val fos: FileOutputStream? = null
            var bitmap: Bitmap? = null
            var croppedBitmap: Bitmap? = null

            try {
                mImageReader!!.acquireLatestImage().use { image ->
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * mWidth


                        // create bitmap
                        bitmap = Bitmap.createBitmap(
                            mWidth + rowPadding / pixelStride,
                            mHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap!!.copyPixelsFromBuffer(buffer)

                        val a = mWidth
                        val b = mHeight
                        val i = cropWidth
                        val j = cropHeight
                        val k = cropTop
                        val l = cropLeft

                        croppedBitmap = Bitmap.createBitmap(
                            bitmap!!, cropLeft, cropTop,
                            cropWidth, cropHeight)

                        //
                        Toast.makeText(this@FloatingWindow, "Stop projection about to be called", Toast.LENGTH_SHORT).show()

                        //TODO Save to internal storage
                        saveImageToInternalStorage(croppedBitmap!!)


                        //TODO don't stop projection till user clicks close
                        //stopProjection()


                        //Do our stuffs
                        /*
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                .toString() + "/Screenshots",
                            "${System.currentTimeMillis()}.jpg"
                        )
                        try {
                            FileOutputStream(file).use { out ->
                                croppedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                out.flush()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        Log.e("STORAGE", "abc")
                        Log.e("STORAGE", file.toString())
                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        val contentUri = Uri.fromFile(file)
                        mediaScanIntent.data = contentUri
                        sendBroadcast(mediaScanIntent)*/
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (fos != null) {
                    try {
                        fos!!.close()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                }
                if (bitmap != null) {
                    bitmap!!.recycle()
                }
            }
        }
    }

    private inner class OrientationChangeCallback internal constructor(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = mDisplay!!.rotation
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                    if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    //TODO Test if this is the reason for double images
                    createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler!!.post {
                if (mVirtualDisplay != null) mVirtualDisplay!!.release()
                if (mImageReader != null) mImageReader!!.setOnImageAvailableListener(null, null)
                if (mOrientationChangeCallback != null) mOrientationChangeCallback!!.disable()
                mMediaProjection!!.unregisterCallback(this@MediaProjectionStopCallback)
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (isStartCommand(intent)) {
            // create notification
            val notification: Pair<Int, Notification> = NotificationUtils.getNotification(this)
            startForeground(notification.first, notification.second)
            // start projection
            Toast.makeText(this, "I am here 1", Toast.LENGTH_SHORT).show()

            resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
            data = intent.getParcelableExtra<Intent>(DATA)!!
            //TODO start projection here
            //startProjection(resultCode, data)
        } else if (isStopCommand(intent)) {
            //stopProjection()
            stopSelf()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent?) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data!!)

            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().displayMetrics.densityDpi
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.defaultDisplay

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register orientation change callback
                mOrientationChangeCallback =
                    OrientationChangeCallback(
                        this
                    )
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }

                // register media projection stop callback
                mMediaProjection!!.registerCallback(
                    MediaProjectionStopCallback(),
                    mHandler
                )
            }
        }
    }

    private fun stopProjection() {
        if (mHandler != null) {
            mHandler!!.post {
                if (mMediaProjection != null) {
                    mMediaProjection!!.stop()
                }
            }
        }
        Toast.makeText(this, "Projection has been stopped", Toast.LENGTH_SHORT).show()
    }

    //TODO SELECT NUMBER OF IMAGES
    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().displayMetrics.widthPixels
        mHeight = Resources.getSystem().displayMetrics.heightPixels

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1)
        //mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.JPEG, 1)
        //mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat., 1)

        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME, mWidth, mHeight,
            mDensity, virtualDisplayFlags, mImageReader!!.surface, null, mHandler
        )
        mImageReader!!.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
    }

    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        return currentTime.format(formatter)
    }

    private fun saveImageToInternalStorage(bmp: Bitmap): Boolean{
        latestScannedImagePath = getCurrentDateTime()
        //val designatedFileName = getCurrentDateTime()
        return try {
            //
            openFileOutput("SCAN.$latestScannedImagePath.jpg", MODE_PRIVATE).use { stream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)){
                    Toast.makeText(this,"Couldn't save bitmap.", Toast.LENGTH_SHORT).show()
                    throw IOException("Couldn't save bitmap.")
                }else{
                    launchAppAndPassData()
                }
            }
            true
        }catch (e: IOException){
            e.printStackTrace()
            false
        }
    }

    private fun launchAppAndPassData(){
        //TODO
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.putExtra("ScannedImageURI", latestScannedImagePath)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(mainIntent)
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val DATA = "DATA"
        private const val ACTION = "ACTION"
        private const val START = "START"
        private const val STOP = "STOP"
        private const val SCREENCAP_NAME = "screencap"
        private var IMAGES_PRODUCED = 0

        fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
            val intent = Intent(context, FloatingWindow::class.java)
            intent.putExtra(ACTION, START)
            intent.putExtra(RESULT_CODE, resultCode)
            intent.putExtra(DATA, data)
            return intent
        }

        fun getStopIntent(context: Context?): Intent {
            val intent = Intent(context, FloatingWindow::class.java)
            intent.putExtra(ACTION, STOP)
            return intent
        }

        private fun isStartCommand(intent: Intent): Boolean {
            return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                    && intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == START)
        }

        private fun isStopCommand(intent: Intent): Boolean {
            return intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP
        }

        private val virtualDisplayFlags: Int
            get() = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }
}