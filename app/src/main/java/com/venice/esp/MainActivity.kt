package com.venice.esp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var overlayRow: TextView
    private lateinit var notifRow: TextView
    private lateinit var battRow: TextView
    private lateinit var startBtn: Button
    private var pendingStart = false

    private val capture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                val i = Intent(this, EspService::class.java).apply {
                    action = EspService.ACTION_START
                    putExtra(EspService.EXTRA_CODE, res.resultCode)
                    putExtra(EspService.EXTRA_DATA, res.data)
                }
                ContextCompat.startForegroundService(this, i)
                status.text = "Running. Open Blood Strike."
            } else {
                status.text = "Capture permission denied."
            }
            refresh()
        }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (pendingStart) { pendingStart = false; continueStart() }
            refresh()
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Cfg.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 110, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "Blood Strike ESP"
            textSize = 26f
            setTextColor(0xFF00E58A.toInt())
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            text = "Grant all three items, then start."
            setTextColor(0xFF8FA3B0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 40)
        }
        root.addView(status)

        overlayRow = row(root)
        overlayRow.setOnClickListener { openOverlaySettings() }

        notifRow = row(root)
        notifRow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        battRow = row(root)
        battRow.setOnClickListener { askBattery() }

        startBtn = btn("Start ESP") { continueStart() }
        root.addView(startBtn)

        root.addView(btn("Stop ESP") {
            startService(Intent(this, EspService::class.java).apply { action = EspService.ACTION_STOP })
            status.text = "Stopped."
        })

        root.addView(TextView(this).apply {
            text = "\nIf boxes lag behind enemies, raise capture FPS and keep fast mode on."
            setTextColor(0xFF5E6E7A.toInt())
            textSize = 12f
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val nf = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val bt = (getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

        overlayRow.text = (if (ov) "\u2713 " else "\u2717 ") + "Overlay permission — tap"
        overlayRow.setTextColor(if (ov) 0xFF00E58A.toInt() else 0xFFFF6B6B.toInt())

        notifRow.text = (if (nf) "\u2713 " else "\u2717 ") + "Notifications — tap"
        notifRow.setTextColor(if (nf) 0xFF00E58A.toInt() else 0xFFFF6B6B.toInt())

        battRow.text = (if (bt) "\u2713 " else "\u2717 ") + "Battery: unrestricted — tap"
        battRow.setTextColor(if (bt) 0xFF00E58A.toInt() else 0xFFFF6B6B.toInt())

        startBtn.isEnabled = ov && nf
    }

    private fun continueStart() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Overlay permission missing — grant it first."
            openOverlaySettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = true
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val mgr = getSystemService(MediaProjectionManager::class.java)
        capture.launch(mgr.createScreenCaptureIntent())
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    @Suppress("BatteryLife")
    private fun askBattery() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun row(parent: LinearLayout): TextView {
        val tv = TextView(this).apply {
            textSize = 15f
            setPadding(0, 22, 0, 22)
        }
        parent.addView(tv)
        return tv
    }

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply { text = label }
        b.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 28 }
        b.setOnClickListener { onClick() }
        return b
    }
}
