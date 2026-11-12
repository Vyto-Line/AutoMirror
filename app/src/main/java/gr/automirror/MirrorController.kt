package gr.automirror

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface

/**
 * Connects the two pieces:
 *  - the MediaProjection (phone screen capture)
 *  - the Surface Android Auto provides (the car screen)
 * When both exist, the phone screen is mirrored onto the car's Surface.
 */
object MirrorController {

    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var carSurface: Surface? = null
    private var carWidth = 0
    private var carHeight = 0
    private var carDpi = 160

    private val listeners = mutableSetOf<() -> Unit>()

    val isProjecting: Boolean get() = projection != null
    val isCarConnected: Boolean get() = carSurface != null

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() = main.post { listeners.toList().forEach { it() } }

    // ---------- Phone side ----------

    @Synchronized
    fun setProjection(p: MediaProjection?) {
        if (p == null) {
            virtualDisplay?.release()
            virtualDisplay = null
        }
        projection = p
        updateDisplay()
        notifyChanged()
    }

    // ---------- Car side ----------

    @Synchronized
    fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Int) {
        carSurface = surface
        carWidth = width
        carHeight = height
        if (dpi > 0) carDpi = dpi
        updateDisplay()
        notifyChanged()
    }

    @Synchronized
    fun onCarSurfaceDestroyed() {
        carSurface = null
        // On Android 14+ each MediaProjection allows only ONE VirtualDisplay,
        // so we don't destroy it: we just detach it from the Surface.
        virtualDisplay?.surface = null
        notifyChanged()
    }

    private fun updateDisplay() {
        val p = projection ?: return
        val surface = carSurface
        val vd = virtualDisplay
        if (vd == null) {
            if (surface == null || carWidth == 0) return
            virtualDisplay = p.createVirtualDisplay(
                "AutoMirror",
                carWidth, carHeight, carDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
        } else if (surface != null) {
            vd.resize(carWidth, carHeight, carDpi)
            vd.surface = surface
        }
    }

    @Synchronized
    fun stopAll() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
        notifyChanged()
    }

    // ---------- Car -> phone coordinate mapping ----------

    /** Phone screen size (takes the current rotation into account). */
    @Suppress("DEPRECATION")
    private fun phoneSize(): Point {
        val dm = appContext.getSystemService(DisplayManager::class.java)
        val p = Point()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(p)
        return p
    }

    /**
     * Mirroring fits the phone picture inside the car screen
     * with black bars. We use the same scale to map touches.
     */
    data class Mapping(val scale: Float, val offX: Float, val offY: Float, val phoneW: Int, val phoneH: Int)

    fun mapping(): Mapping? {
        if (carWidth == 0 || carHeight == 0) return null
        val ph = phoneSize()
        val scale = minOf(carWidth.toFloat() / ph.x, carHeight.toFloat() / ph.y)
        val offX = (carWidth - ph.x * scale) / 2f
        val offY = (carHeight - ph.y * scale) / 2f
        return Mapping(scale, offX, offY, ph.x, ph.y)
    }

    /** The point on the phone, or null if the touch landed on the black bars. */
    fun carToPhone(x: Float, y: Float): Pair<Float, Float>? {
        val m = mapping() ?: return null
        val px = (x - m.offX) / m.scale
        val py = (y - m.offY) / m.scale
        if (px < 0 || py < 0 || px >= m.phoneW || py >= m.phoneH) return null
        return px to py
    }
}
