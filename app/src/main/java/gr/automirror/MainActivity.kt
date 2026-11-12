package gr.automirror

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private val listener: () -> Unit = { refresh() }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                val i = Intent(this, ProjectionService::class.java)
                    .putExtra(ProjectionService.EXTRA_CODE, result.resultCode)
                    .putExtra(ProjectionService.EXTRA_DATA, data)
                ContextCompat.startForegroundService(this, i)
            }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MirrorController.init(this)

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "AutoMirror"
            textSize = 26f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad)
        }
        root.addView(status)

        root.addView(button("1. Turn on touch (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("2. Start mirroring") { startMirroring() })
        root.addView(button("Stop") {
            startService(Intent(this, ProjectionService::class.java)
                .setAction(ProjectionService.ACTION_STOP))
        })

        root.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = """
                How to use:
                • Connect to Android Auto and open “AutoMirror” from the car's app menu.
                • Tap “Start mirroring” and choose “Entire screen”.
                • A touch on the car screen = a tap on the phone.
                • To scroll: tap the pan button (cross arrows) on Android Auto and drag.
                • Only use it while the car is parked.
            """.trimIndent()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun startMirroring() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            // Forces capture of the entire screen (not a single app)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        MirrorController.addListener(listener)
        refresh()
    }

    override fun onPause() {
        MirrorController.removeListener(listener)
        super.onPause()
    }

    /** Android Auto shows the app only if Play is the installer and the shell didn't start it. */
    private fun hasPlayMark(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return true
        return try {
            val info = packageManager.getInstallSourceInfo(packageName)
            info.installingPackageName == "com.android.vending" &&
                info.initiatingPackageName != "com.android.shell"
        } catch (e: Exception) {
            false
        }
    }

    private fun refresh() {
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        status.text = """
            ${mark(TouchInjectService.instance != null)} Touch from the car
            ${mark(MirrorController.isCarConnected)} Android Auto connected (app open)
            ${mark(MirrorController.isProjecting)} Screen capture running
            ${mark(hasPlayMark())} Install accepted by Android Auto
        """.trimIndent()
    }
}
