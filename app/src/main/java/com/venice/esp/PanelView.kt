package com.venice.esp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PanelView(ctx: Context, private val wm: WindowManager) : LinearLayout(ctx) {

    private var params: WindowManager.LayoutParams? = null
    private var collapsed = false
    private var dx = 0
    private var dy = 0

    init {
        orientation = VERTICAL
        setPadding(d(12), d(10), d(12), d(12))
        background = GradientDrawable().apply {
            cornerRadius = d(14).toFloat()
            setColor(0xE6101418.toInt())
            setStroke(d(1), 0xFF00E58A.toInt())
        }
        elevation = d(8).toFloat()

        val header = TextView(ctx).apply {
            text = "BS ESP  \u2715"
            setTextColor(0xFF00E58A.toInt())
            textSize = 13f
            setPadding(d(2), d(2), d(2), d(6))
        }
        addView(header)

        header.setOnTouchListener { _, e ->
            val lp = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX.toInt() - lp.x
                    dy = e.rawY.toInt() - lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = e.rawX.toInt() - dx
                    lp.y = e.rawY.toInt() - dy
                    runCatching { wm.updateViewLayout(this, lp) }
                }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(e.rawX.toInt() - dx - lp.x) < d(6)) {
                        collapsed = !collapsed
                        for (i in 1 until childCount) getChildAt(i).visibility =
                            if (collapsed) View.GONE else View.VISIBLE
                    }
                }
            }
            true
        }

        addSwitch("ESP master", Cfg.enabled) { Cfg.enabled = it; persist() }
        addSwitch("Boxes", Cfg.boxes) { Cfg.boxes = it; persist() }
        addSwitch("Snaplines", Cfg.snaplines) { Cfg.snaplines = it; persist() }
        addSwitch("Distance", Cfg.distance) { Cfg.distance = it; persist() }
        addSwitch("FOV ring", Cfg.fovRing) { Cfg.fovRing = it; persist() }
        addSwitch("Fast mode (480p)", Cfg.fastMode) { Cfg.fastMode = it; persist() }

        addSlider("FOV size", (Cfg.fovRadius * 100).toInt(), 10, 60) {
            Cfg.fovRadius = it / 100f; persist()
        }
        addSlider("Confidence", (Cfg.threshold * 100).toInt(), 25, 90) {
            Cfg.threshold = it / 100f; persist()
        }
        addSlider("Capture FPS", Cfg.fps, 3, 15) {
            Cfg.fps = it; persist()
        }

        val colorBtn = TextView(ctx).apply {
            text = "  Colour  "
            setTextColor(Color.BLACK)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(d(6), d(6), d(6), d(6))
            background = GradientDrawable().apply {
                cornerRadius = d(8).toFloat()
                setColor(Cfg.color)
            }
            setOnClickListener {
                Cfg.cycleColor()
                (background as GradientDrawable).setColor(Cfg.color)
                persist()
            }
        }
        addView(colorBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = d(8)
        })
    }

    fun attach(lp: WindowManager.LayoutParams) { params = lp }

    private fun persist() = Cfg.save(context)

    private fun addSwitch(label: String, init: Boolean, onChange: (Boolean) -> Unit) {
        val sw = SwitchCompat(context).apply {
            text = label
            setTextColor(0xFFE6EDF3.toInt())
            textSize = 12f
            isChecked = init
            setPadding(0, d(2), 0, d(2))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        addView(sw)
    }

    private fun addSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
        val tv = TextView(context).apply {
            text = "$label  $value"
            setTextColor(0xFF8FA3B0.toInt())
            textSize = 11f
        }
        val sb = SeekBar(context).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    tv.text = "$label  $v"
                    onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        addView(tv)
        addView(sb)
    }

    private fun d(v: Int) = (v * resources.displayMetrics.density).toInt()
}
