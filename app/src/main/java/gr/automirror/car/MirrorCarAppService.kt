package gr.automirror.car

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import gr.automirror.MirrorController
import gr.automirror.TouchInjectService
import kotlin.math.abs

class MirrorCarAppService : CarAppService() {
    // Η εφαρμογή εγκαθίσταται εκτός Play Store, οπότε δεχόμαστε κάθε host.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = MirrorSession()
}

class MirrorSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
}

/**
 * Οθόνη τύπου "πλοήγησης": το Android Auto μας δίνει ένα Surface ολόκληρης της οθόνης,
 * στο οποίο προβάλλουμε το κινητό. Τα αγγίγματα επιστρέφουν μέσω του SurfaceCallback.
 */
class MirrorScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val main = Handler(Looper.getMainLooper())
    private val stateListener: () -> Unit = { invalidate() }

    // Συσσώρευση κύλισης ώστε να στέλνουμε λίγα, ομαλά swipes
    private var pendingDx = 0f
    private var pendingDy = 0f
    private val flushScroll = Runnable { sendPendingScroll() }

    init {
        MirrorController.init(carContext)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = MirrorController.addListener(stateListener)
            override fun onStop(owner: LifecycleOwner) = MirrorController.removeListener(stateListener)
        })
    }

    override fun onGetTemplate(): Template {
        val label = when {
            !MirrorController.isProjecting -> "Πάτα «Έναρξη» στο κινητό"
            TouchInjectService.instance == null -> "Mirroring (χωρίς αφή)"
            else -> "Mirroring ενεργό"
        }
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle(label).setOnClickListener { invalidate() }.build())
            .build()

        val builder = NavigationTemplate.Builder().setActionStrip(strip)
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            // Το κουμπί PAN ενεργοποιεί τη λειτουργία μετακίνησης => παίρνουμε onScroll/onFling
            builder.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
        }
        return builder.build()
    }

    // ---------- Surface ----------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val s = surfaceContainer.surface ?: return
        MirrorController.onCarSurfaceAvailable(
            s, surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi
        )
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        MirrorController.onCarSurfaceDestroyed()
    }

    // ---------- Αφή ----------

    override fun onClick(x: Float, y: Float) {
        val svc = TouchInjectService.instance ?: return
        val (px, py) = MirrorController.carToPhone(x, y) ?: return
        svc.tap(px, py)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        // distance = προηγούμενη θέση − τρέχουσα (όπως στο GestureDetector)
        pendingDx -= distanceX
        pendingDy -= distanceY
        main.removeCallbacks(flushScroll)
        main.postDelayed(flushScroll, 70)
    }

    private fun sendPendingScroll() {
        val svc = TouchInjectService.instance
        val m = MirrorController.mapping()
        if (svc != null && m != null && (abs(pendingDx) > 2 || abs(pendingDy) > 2)) {
            val cx = m.phoneW / 2f
            val cy = m.phoneH / 2f
            svc.swipe(cx, cy, cx + pendingDx / m.scale, cy + pendingDy / m.scale, 250)
        }
        pendingDx = 0f
        pendingDy = 0f
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        main.removeCallbacks(flushScroll)
        pendingDx = 0f
        pendingDy = 0f
        val svc = TouchInjectService.instance ?: return
        val m = MirrorController.mapping() ?: return
        val dx = (velocityX * 0.12f / m.scale).coerceIn(-m.phoneW * 0.45f, m.phoneW * 0.45f)
        val dy = (velocityY * 0.12f / m.scale).coerceIn(-m.phoneH * 0.45f, m.phoneH * 0.45f)
        val cx = m.phoneW / 2f
        val cy = m.phoneH / 2f
        svc.swipe(cx - dx / 2, cy - dy / 2, cx + dx / 2, cy + dy / 2, 90)
    }
}
