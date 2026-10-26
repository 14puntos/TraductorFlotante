package com.traductor.flotante

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class SelectorView(
    context: Context,
    private val onDone: (Rect?) -> Unit
) : View(context) {
    private val dim = Color.parseColor("#66000000")
    private val fill = Paint().apply { color = Color.parseColor("#3300AAFF") }
    private val border = Paint().apply {
        color = Color.parseColor("#FF00AAFF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val hint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private var sx = 0f
    private var sy = 0f
    private var cx = 0f
    private var cy = 0f
    private var rawSx = 0f
    private var rawSy = 0f
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(dim)
        if (!dragging) {
            canvas.drawText(
                "Arrastra para marcar el cuadro de texto",
                width / 2f, height / 2f, hint
            )
            canvas.drawText(
                "(toca sin arrastrar para cancelar)",
                width / 2f, height / 2f + 64f, hint
            )
        } else {
            val l = minOf(sx, cx)
            val t = minOf(sy, cy)
            val r = maxOf(sx, cx)
            val b = maxOf(sy, cy)
            canvas.drawRect(l, t, r, b, fill)
            canvas.drawRect(l, t, r, b, border)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x
                sy = e.y
                cx = e.x
                cy = e.y
                rawSx = e.rawX
                rawSy = e.rawY
                dragging = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                cx = e.x
                cy = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                val rect = Rect(
                    minOf(rawSx, e.rawX).toInt(),
                    minOf(rawSy, e.rawY).toInt(),
                    maxOf(rawSx, e.rawX).toInt(),
                    maxOf(rawSy, e.rawY).toInt()
                )
                onDone(if (rect.width() < 100 || rect.height() < 60) null else rect)
            }
        }
        return true
    }
}

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var icon: TextView? = null
    private var selector: SelectorView? = null
    private var frame: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayParams(w: Int, h: Int, extraFlags: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            extraFlags
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    private fun startSelection() {
        if (selector != null) return
        val sv = SelectorView(this) { rect -> finishSelection(rect) }
        wm.addView(
            sv,
            overlayParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                0
            )
        )
        selector = sv
    }

    private fun finishSelection(rect: Rect?) {
        handler.post {
            selector?.let { wm.removeView(it) }
            selector = null
            if (rect == null) {
                Toast.makeText(this, "Selección cancelada", Toast.LENGTH_SHORT).show()
            } else {
                getSharedPreferences("zona", MODE_PRIVATE).edit()
                    .putInt("left", rect.left)
                    .putInt("top", rect.top)
                    .putInt("right", rect.right)
                    .putInt("bottom", rect.bottom)
                    .apply()
                showFrame(rect)
                Toast.makeText(this, "Zona guardada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFrame(rect: Rect) {
        frame?.let { wm.removeView(it) }
        val v = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(6, Color.parseColor("#FF00AAFF"))
            }
        }
        val p = overlayParams(
            rect.width(), rect.height(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            x = rect.left
            y = rect.top
        }
        wm.addView(v, p)
        frame = v
        handler.postDelayed({
            frame?.let { wm.removeView(it) }
            frame = null
        }, 2500)
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (56 * resources.displayMetrics.density).toInt()

        val view = TextView(this).apply {
            text = "Tr"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC1565C0"))
            }
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) startSelection()
                }
            }
            true
        }

        wm.addView(view, params)
        icon = view
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        selector?.let { wm.removeView(it) }
        frame?.let { wm.removeView(it) }
        icon?.let { wm.removeView(it) }
        selector = null
        frame = null
        icon = null
        super.onDestroy()
    }
}
