package com.easit.aiscanner.floatingWindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import com.easit.aiscanner.R

class Floater: android.app.Service() {
    var viewRoot: View? = null
    var windowManager: WindowManager? = null
    var rootParams: WindowManager.LayoutParams? = null
    var imageView: ImageView? = null
    var close: LinearLayout? = null
    var textView: TextView? = null
    var fab: LinearLayout? = null
    var imageText: LinearLayout? = null
    var audioText: LinearLayout? = null
    //Phase 2
    private var mHandler: Handler? = null
    var fWidth = 0


    override fun onBind(p0: Intent?): IBinder? {
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
                    //setupScreenCropper()
                    //TODO START TRANSPARENT ACTIVITY

                    val intentMain = Intent(this, ImageTransparentActivity::class.java)
                    intentMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intentMain)

                    //ImageTransparentActivity().start
                    //ImageTransparentActivity().startProjection()
                    //(activity as ImageTransparentActivity).issues()
                    //issues()
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

    private fun stopService() {
        try {
            stopForeground(true)
            stopSelf()
            windowManager!!.removeViewImmediate(viewRoot)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun issues(){
        //Unknown action: To remove later
        var DeX = false
        val config = resources.configuration
        try {
            val configClass: Class<*> = config.javaClass
            if (configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass) ==
                configClass.getField("semDesktopModeEnabled").getInt(config)
            ) {
                DeX = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        //startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
        startActivity(mProjectionManager.createScreenCaptureIntent())
    }

}