package com.venice.esp

import android.content.Context

object Cfg {
    var enabled = true
    var boxes = true
    var snaplines = true
    var distance = true
    var fovRing = true
    var fovRadius = 0.30f
    var threshold = 0.45f
    var fps = 8
    var fastMode = true
    var color = 0xFF00FF88.toInt()

    private val colors = intArrayOf(
        0xFF00FF88.toInt(), 0xFFFF3B3B.toInt(), 0xFF3BA9FF.toInt(),
        0xFFFFD23B.toInt(), 0xFFC93BFF.toInt()
    )

    fun cycleColor() {
        color = colors[(colors.indexOf(color) + 1) % colors.size]
    }

    fun load(c: Context) {
        val p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        boxes = p.getBoolean("boxes", true)
        snaplines = p.getBoolean("snaplines", true)
        distance = p.getBoolean("distance", true)
        fovRing = p.getBoolean("fov", true)
        fovRadius = p.getFloat("fovR", 0.30f)
        threshold = p.getFloat("thr", 0.45f)
        fps = p.getInt("fps", 8)
        fastMode = p.getBoolean("fast", true)
        color = p.getInt("color", 0xFF00FF88.toInt())
    }

    fun save(c: Context) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
            .putBoolean("boxes", boxes).putBoolean("snaplines", snaplines)
            .putBoolean("distance", distance).putBoolean("fov", fovRing)
            .putFloat("fovR", fovRadius).putFloat("thr", threshold)
            .putInt("fps", fps).putBoolean("fast", fastMode).putInt("color", color).apply()
    }
}
