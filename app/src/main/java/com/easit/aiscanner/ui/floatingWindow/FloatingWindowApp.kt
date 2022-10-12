package com.easit.aiscanner.ui.floatingWindow

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.easit.aiscanner.R
import com.google.android.material.chip.ChipGroup

class FloatingWindowApp : Service() {

    private lateinit var floatView: ViewGroup
    private lateinit var floatWindowLayoutParams: WindowManager.LayoutParams
    private var layoutType: Int? = null
    private lateinit var windowManager: WindowManager

    private lateinit var minMaxEntity: Button
    private lateinit var minMaxSmartReply: Button
    private lateinit var minMaxTranslation: Button

    private lateinit var cardView: CardView
    private lateinit var translationTextview: TextView
    private lateinit var translationCopy: ImageButton
    private lateinit var entityTextview: TextView
    private lateinit var entityCopy: ImageButton
    private lateinit var smartReplyGroup: ChipGroup

    private lateinit var openTranslation: ImageButton
    private lateinit var openEntity: ImageButton
    private lateinit var openReplies: ImageButton

    private lateinit var closeLayout: ImageButton

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatView = inflater.inflate(R.layout.scanner_v1, null) as ViewGroup

        openTranslation = floatView.findViewById(R.id.openTranslation)
        openEntity = floatView.findViewById(R.id.openEntity)
        openReplies = floatView.findViewById(R.id.openReplies)

        cardView = floatView.findViewById(R.id.cardView)
        translationTextview = floatView.findViewById(R.id.translationTextview)
        translationCopy = floatView.findViewById(R.id.translationCopy)
        entityTextview = floatView.findViewById(R.id.entityTextview)
        entityCopy = floatView.findViewById(R.id.entityCopy)
        //smartReplyGroup = floatView.findViewById(R.id.smartReplyGroup)

        closeLayout = floatView.findViewById(R.id.closeLayout)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        //else LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_TOAST
        else layoutType = WindowManager.LayoutParams.TYPE_PHONE

        floatWindowLayoutParams = WindowManager.LayoutParams(
            (width * 0.55F).toInt(),
            (height * 0.55F).toInt(),
            layoutType!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT /**To try as transparent later*/
        )
        floatWindowLayoutParams.gravity = Gravity.CENTER
        floatWindowLayoutParams.x = 0
        floatWindowLayoutParams.y = 0

        windowManager.addView(floatView, floatWindowLayoutParams)

        closeLayout.setOnClickListener {
            stopSelf()
            windowManager.removeView(floatView)
        }

        floatView.setOnTouchListener(object : View.OnTouchListener{

            val updatedFloatWindowLayoutParam = floatWindowLayoutParams
            var x = 0.0
            var y = 0.0
            var px = 0.0
            var py = 0.0


            override fun onTouch(p0: View?, event: MotionEvent?): Boolean {
                when(event!!.action){
                    MotionEvent.ACTION_DOWN -> {
                        x = updatedFloatWindowLayoutParam.x.toDouble()
                        y = updatedFloatWindowLayoutParam.y.toDouble()

                        px = event.rawX.toDouble()
                        py = event.rawY.toDouble()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updatedFloatWindowLayoutParam.x = (x + event.rawX - px).toInt()
                        updatedFloatWindowLayoutParam.y = (x + event.rawY - py).toInt()
                        windowManager.updateViewLayout(floatView, updatedFloatWindowLayoutParam)
                    }
                }
                return false
            }
        })

    }

}