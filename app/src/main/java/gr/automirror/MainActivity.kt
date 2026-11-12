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

        root.addView(button("1. Ενεργοποίηση αφής (Προσβασιμότητα)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("2. Έναρξη mirroring") { startMirroring() })
        root.addView(button("Διακοπή") {
            startService(Intent(this, ProjectionService::class.java)
                .setAction(ProjectionService.ACTION_STOP))
        })

        root.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = """
                Οδηγίες:
                • Συνδέσου στο Android Auto και άνοιξε την εφαρμογή «AutoMirror» από το μενού εφαρμογών του αυτοκινήτου.
                • Πάτα «Έναρξη mirroring» και διάλεξε «Ολόκληρη οθόνη».
                • Άγγιγμα στην οθόνη του αυτοκινήτου = πάτημα στο κινητό.
                • Για κύλιση: πάτα το κουμπί μετακίνησης (σταυρός) στο Android Auto και σύρε.
                • Χρησιμοποίησέ το μόνο όταν το αυτοκίνητο είναι σταματημένο.
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
            // Επιβάλλει καταγραφή ολόκληρης οθόνης (όχι μίας εφαρμογής)
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
            ${mark(TouchInjectService.instance != null)} Αφή από το αυτοκίνητο
            ${mark(MirrorController.isCarConnected)} Android Auto συνδεδεμένο (εφαρμογή ανοιχτή)
            ${mark(MirrorController.isProjecting)} Καταγραφή οθόνης ενεργή
            ${mark(hasPlayMark())} Εγκατάσταση αποδεκτή από Android Auto
        """.trimIndent()
    }
}
