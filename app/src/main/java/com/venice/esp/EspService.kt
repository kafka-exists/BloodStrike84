package com.venice.esp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EspService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CH = "esp"
        private const val ID = 7
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var overlay: OverlayView? = null
    private var panel: PanelView? = null
    private var detector: Detector? = null
    private var lastFrame = 0L
    private var capW = 0
    private var capH = 0

    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        if (overlay != null) return START_NOT_STICKY

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Cfg.load(this)
        buildOverlay()
        buildPanel()

        try {
            startProjection(intent)
        } catch (t: Throwable) {
            overlay?.statusText = "setup failed: ${t.javaClass.simpleName}"
            overlay?.postInvalidate()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "ESP", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setContentTitle("BS ESP active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, ID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )
    }

    private fun buildOverlay() {
        val themed = ContextThemeWrapper(this, R.style.Theme_BSEsp)
        val o = OverlayView(themed)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(o, lp)
        overlay = o
    }

    private fun buildPanel() {
        val themed = ContextThemeWrapper(this, R.style.Theme_BSEsp)
        val p = PanelView(themed, wm)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 24
            y = 180
        }
        p.attach(lp)
        wm.addView(p, lp)
        panel = p
    }

    private fun startProjection(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA) ?: return
        val mgr = getSystemService(MediaProjectionManager::class.java)
        val proj = mgr.getMediaProjection(code, data) ?: return
        projection = proj

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(mainLooper))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val scale = (if (Cfg.fastMode) 480f else 720f) / metrics.widthPixels
        capW = if (Cfg.fastMode) 480 else 720
        capH = (metrics.heightPixels * scale).toInt().let { if (it % 2 == 0) it else it + 1 }

        val ht = HandlerThread("cap").also { it.start() }
        thread = ht

        val r = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 3)
        reader = r
        r.setOnImageAvailableListener({ onFrame(it) }, Handler(ht.looper))

        display = proj.createVirtualDisplay(
            "esp", capW, capH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, Handler(ht.looper)
        )

        overlay?.statusText = "loading model…"
        detector = Detector(this)
        scope.launch {
            val ok = try { detector?.init() == true } catch (t: Throwable) { false }
            Handler(mainLooper).post {
                overlay?.statusText = if (ok) null else "model download failed"
            }
        }
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrame < 1000L / Cfg.fps.coerceAtLeast(1)) {
            img.close()
            return
        }
        lastFrame = now

        val bmp = try {
            toBitmap(img)
        } catch (t: Throwable) {
            img.close()
            return
        }
        img.close()

        scope.launch {
            val hits = try {
                detector?.detect(bmp) ?: emptyList()
            } catch (t: Throwable) {
                emptyList()
            }
            bmp.recycle()
            if (hits.isEmpty()) {
                Handler(mainLooper).post { overlay?.let { it.dets = emptyList(); it.postInvalidate() } }
                return@launch
            }
            val norm = hits.map {
                RectF(
                    it.left / capW.toFloat(), it.top / capH.toFloat(),
                    it.right / capW.toFloat(), it.bottom / capH.toFloat()
                )
            }
            Handler(mainLooper).post {
                overlay?.let { it.dets = norm; it.postInvalidate() }
            }
        }
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride
        val pad = rs - ps * image.width
        val w = image.width + pad / ps
        val bmp = Bitmap.createBitmap(w, image.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        return if (w != image.width) {
            val c = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
            bmp.recycle()
            c
        } else bmp
    }

    override fun onDestroy() {
        Cfg.save(this)
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        thread?.quitSafely()
        detector?.close()
        panel?.let { runCatching { wm.removeView(it) } }
        overlay?.let { runCatching { wm.removeView(it) } }
        panel = null
        overlay = null
        scope.cancel()
        super.onDestroy()
    }
}
