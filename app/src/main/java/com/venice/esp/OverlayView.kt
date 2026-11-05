package com.venice.esp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class OverlayView(ctx: Context) : View(ctx) {

    @Volatile var dets: List<RectF> = emptyList()
    @Volatile var statusText: String? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        statusText?.let {
            txt.color = 0xFFFFFFFF.toInt()
            fill.color = 0xCC000000.toInt()
            canvas.drawRoundRect(RectF(24f, 24f, 24f + txt.measureText(it) + 40f, 100f), 20f, 20f, fill)
            canvas.drawText(it, 44f, 78f, txt)
        }

        if (!Cfg.enabled) return
        val c = Cfg.color
        p.color = c
        fill.color = c
        txt.color = c

        if (Cfg.fovRing) {
            p.alpha = 120
            canvas.drawCircle(w / 2f, h, Cfg.fovRadius * h, p)
            p.alpha = 255
        }

        for (r in dets) {
            val box = RectF(r.left * w, r.top * h, r.right * w, r.bottom * h)
            val cx = box.centerX()
            if (Cfg.snaplines) canvas.drawLine(w / 2f, h, cx, box.bottom, p)
            if (Cfg.boxes) drawCorners(canvas, box)
            if (Cfg.distance) {
                val dist = (1200f / box.height().coerceAtLeast(1f)).coerceIn(1f, 200f)
                canvas.drawText("${dist.toInt()}m", box.left, box.top - 10f, txt)
            }
        }
    }

    private fun drawCorners(canvas: Canvas, b: RectF) {
        val L = (b.width() * 0.28f).coerceAtMost(40f)
        canvas.drawLine(b.left, b.top, b.left + L, b.top, p)
        canvas.drawLine(b.left, b.top, b.left, b.top + L, p)
        canvas.drawLine(b.right, b.top, b.right - L, b.top, p)
        canvas.drawLine(b.right, b.top, b.right, b.top + L, p)
        canvas.drawLine(b.left, b.bottom, b.left + L, b.bottom, p)
        canvas.drawLine(b.left, b.bottom, b.left, b.bottom - L, p)
        canvas.drawLine(b.right, b.bottom, b.right - L, b.bottom, p)
        canvas.drawLine(b.right, b.bottom, b.right, b.bottom - L, p)
    }
}
