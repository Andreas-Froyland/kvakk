package app.kvakk.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import app.kvakk.R
import app.kvakk.ui.HomeActivity
import kotlin.math.abs

/**
 * Floating duck button drawn over other apps. One tap takes the child back to Kvakk, which is
 * easier for small kids than the system Home button or gesture. Needs "display over other apps".
 */
// The view uses the application context, so holding it here can't leak an activity.
@SuppressLint("StaticFieldLeak")
object HomeBubble {
    private const val SIZE_DP = 64
    private const val MARGIN_DP = 8
    private const val TOUCH_SLOP_DP = 10

    /** Shows only once Kvakk has stayed out of sight this long, so it never flashes over Kvakk. */
    private const val SHOW_DELAY_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    // Where the child last left the bubble; null until it has been dragged.
    private var savedX: Int? = null
    private var savedY: Int? = null
    private var kvakkVisible = 0
    private var pendingShow: Runnable? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Call from HomeActivity's onStart/onStop. Counts instances rather than using a flag: when the
     * activity is recreated, the new instance can start before the old one stops.
     */
    fun onKvakkVisible(context: Context, visible: Boolean, enabled: Boolean) {
        kvakkVisible = (kvakkVisible + if (visible) 1 else -1).coerceAtLeast(0)
        pendingShow?.let(handler::removeCallbacks)
        pendingShow = null
        if (kvakkVisible > 0 || !enabled) {
            hide(context)
            return
        }
        val app = context.applicationContext
        pendingShow = Runnable { if (kvakkVisible == 0) show(app) }.also { handler.postDelayed(it, SHOW_DELAY_MS) }
    }

    @SuppressLint("ClickableViewAccessibility") // DragListener calls performClick() on tap.
    private fun show(context: Context) {
        val app = context.applicationContext
        if (view != null || !canShow(app)) return
        val windowManager = app.getSystemService(WindowManager::class.java)
        val size = app.dp(SIZE_DP)
        val (width, height) = screenSize(app, windowManager)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default: right edge, a third of the way down. Clamped in case the screen rotated.
            x = (savedX ?: (width - size - app.dp(MARGIN_DP))).coerceIn(0, width - size)
            y = (savedY ?: (height / 3)).coerceIn(0, height - size)
        }
        val bubble = ImageView(app).apply {
            setImageResource(R.drawable.ic_duck)
            val padding = app.dp(10)
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFF6E6.toInt())
                setStroke(app.dp(3), 0xFFFF7A00.toInt())
            }
            elevation = app.dp(6).toFloat()
            contentDescription = app.getString(R.string.home_bubble)
            setOnTouchListener(DragListener(app, windowManager, params))
        }
        windowManager.addView(bubble, params)
        view = bubble
    }

    private fun hide(context: Context) {
        val bubble = view ?: return
        context.applicationContext.getSystemService(WindowManager::class.java).removeView(bubble)
        view = null
    }

    private fun goHome(context: Context) {
        context.startActivity(
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private class DragListener(
        private val context: Context,
        private val windowManager: WindowManager,
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > context.dp(TOUCH_SLOP_DP) || abs(dy) > context.dp(TOUCH_SLOP_DP))) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge(view)
                    } else {
                        view.performClick()
                        goHome(context)
                    }
                }
            }
            return true
        }

        private fun snapToEdge(view: View) {
            val (width, height) = screenSize(context, windowManager)
            val margin = context.dp(MARGIN_DP)
            val onLeft = params.x + view.width / 2 < width / 2
            params.x = if (onLeft) margin else width - view.width - margin
            params.y = params.y.coerceIn(0, height - view.height)
            windowManager.updateViewLayout(view, params)
            savedX = params.x
            savedY = params.y
        }
    }

    /** Current window size; the app's display metrics can lag behind a rotation. */
    private fun screenSize(context: Context, windowManager: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }

    private fun Context.dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
