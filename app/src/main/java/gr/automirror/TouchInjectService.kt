package gr.automirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service: taps and swipes on the phone
 * when the user touches the Android Auto screen.
 */
class TouchInjectService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        dispatch(path, 60)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val m = MirrorController.mapping()
        val maxX = (m?.phoneW ?: 10000) - 1f
        val maxY = (m?.phoneH ?: 10000) - 1f
        val path = Path().apply {
            moveTo(x1.coerceIn(0f, maxX), y1.coerceIn(0f, maxY))
            lineTo(x2.coerceIn(0f, maxX), y2.coerceIn(0f, maxY))
        }
        dispatch(path, durationMs)
    }

    private fun dispatch(path: Path, durationMs: Long) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: TouchInjectService? = null
            private set
    }
}
