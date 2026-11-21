package com.hossa.mando

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object C {
    val BG = Color.parseColor("#0C0C0E")
    val SURFACE = Color.parseColor("#18181B")
    val SURFACE_TOP = Color.parseColor("#1E1E22")
    val SURFACE_BOTTOM = Color.parseColor("#151518")
    val RING = Color.parseColor("#141417")
    val TEXT = Color.parseColor("#ECE8E1")
    val ICON = Color.parseColor("#B9B4AC")
    val MUTED = Color.parseColor("#8A867F")
    val DIM = Color.parseColor("#55524D")
    val GOLD = Color.parseColor("#D9B77E")
    val GREEN = Color.parseColor("#7FB38A")
    val AMBER = Color.parseColor("#D9A441")
    val RED = Color.parseColor("#E0605A")
    val HAIR = Color.parseColor("#12FFFFFF")
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.font(medium: Boolean = false): Typeface =
    ResourcesCompat.getFont(this, if (medium) R.font.barlow_medium else R.font.barlow_regular) ?: Typeface.DEFAULT

fun Context.label(text: CharSequence, sp: Float, color: Int = C.TEXT, medium: Boolean = false) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    typeface = font(medium)
}

fun View.haptic() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

/** Botón con relieve suave (redondo o píldora). */
@SuppressLint("ClickableViewAccessibility")
fun Context.softButton(
    iconRes: Int? = null, text: String? = null, sizeDp: Int = 52, iconDp: Int = 22,
    tint: Int = C.TEXT, gold: Boolean = false, oval: Boolean = true, radiusDp: Float = 14f
): FrameLayout {
    val ctx = this
    return FrameLayout(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.SURFACE_TOP, C.SURFACE_BOTTOM)).apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radiusDp)
            setStroke(dpi(1), if (gold) Color.parseColor("#4DD9B77E") else C.HAIR)
        }
        elevation = dp(6f)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isClickable = true
        if (iconRes != null) {
            addView(ImageView(ctx).apply { setImageResource(iconRes); setColorFilter(if (gold) C.GOLD else tint) },
                FrameLayout.LayoutParams(dpi(iconDp), dpi(iconDp), Gravity.CENTER))
        }
        if (text != null) {
            addView(label(text, 15f, if (gold) C.GOLD else C.TEXT, true).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }
}

/** Repite la acción mientras se mantiene presionado (volumen). */
@SuppressLint("ClickableViewAccessibility")
fun View.repeatWhileHeld(action: () -> Unit) {
    val h = Handler(Looper.getMainLooper())
    var runner: Runnable? = null
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                v.haptic(); action()
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70).start()
                val r = object : Runnable {
                    override fun run() { action(); v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); h.postDelayed(this, 140) }
                }
                runner = r
                h.postDelayed(r, 420)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                runner?.let { h.removeCallbacks(it) }
                v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
        }
        true
    }
}

/** Anillo direccional con botón OK al centro. */
class DpadView(ctx: Context, private val onKey: (Int) -> Unit) : View(ctx) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1f); color = C.HAIR }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14FFFFFF") }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ctx.dp(2.4f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val okStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1.2f); color = Color.parseColor("#59D9B77E") }
    private val okText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C.GOLD; textAlign = Paint.Align.CENTER; textSize = ctx.dp(16f); typeface = ctx.font(true); letterSpacing = 0.15f
    }
    private var pressed = -1 // 0 arriba, 1 derecha, 2 abajo, 3 izquierda, 4 OK

    init {
        elevation = ctx.dp(12f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ringPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), C.SURFACE_TOP, Color.parseColor("#111113"), Shader.TileMode.CLAMP)
        okPaint.shader = LinearGradient(0f, h * 0.3f, 0f, h * 0.7f, Color.parseColor("#222226"), Color.parseColor("#18181B"), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(cx, cy) - context.dp(1f)
        val okR = r * 0.43f
        c.drawCircle(cx, cy, r, ringPaint)
        c.drawCircle(cx, cy, r, strokePaint)

        if (pressed in 0..3) {
            val start = -135f + pressed * 90f
            val path = Path().apply {
                arcTo(RectF(cx - r, cy - r, cx + r, cy + r), start, 90f)
                arcTo(RectF(cx - okR, cy - okR, cx + okR, cy + okR), start + 90f, -90f)
                close()
            }
            c.drawPath(path, pressPaint)
        }

        val mid = (r + okR) / 2f
        val s = context.dp(7f)
        for (i in 0..3) {
            arrowPaint.color = if (pressed == i) C.GOLD else C.ICON
            val p = Path()
            when (i) {
                0 -> { p.moveTo(cx - s, cy - mid + s * 0.5f); p.lineTo(cx, cy - mid - s * 0.5f); p.lineTo(cx + s, cy - mid + s * 0.5f) }
                1 -> { p.moveTo(cx + mid - s * 0.5f, cy - s); p.lineTo(cx + mid + s * 0.5f, cy); p.lineTo(cx + mid - s * 0.5f, cy + s) }
                2 -> { p.moveTo(cx - s, cy + mid - s * 0.5f); p.lineTo(cx, cy + mid + s * 0.5f); p.lineTo(cx + s, cy + mid - s * 0.5f) }
                3 -> { p.moveTo(cx - mid + s * 0.5f, cy - s); p.lineTo(cx - mid - s * 0.5f, cy); p.lineTo(cx - mid + s * 0.5f, cy + s) }
            }
            c.drawPath(p, arrowPaint)
        }

        val okScale = if (pressed == 4) 0.95f else 1f
        c.save(); c.scale(okScale, okScale, cx, cy)
        c.drawCircle(cx, cy, okR, okPaint)
        c.drawCircle(cx, cy, okR, okStroke)
        c.drawText("OK", cx, cy - (okText.descent() + okText.ascent()) / 2f, okText)
        c.restore()
    }

    private fun zone(x: Float, y: Float): Int {
        val cx = width / 2f; val cy = height / 2f
        val dx = x - cx; val dy = y - cy
        val r = minOf(cx, cy)
        val d = hypot(dx, dy)
        if (d > r * 1.05f) return -1
        if (d < r * 0.43f) return 4
        val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) // 0 = derecha
        return when {
            ang >= -135 && ang < -45 -> 0
            ang >= -45 && ang < 45 -> 1
            ang >= 45 && ang < 135 -> 2
            else -> 3
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private val repeat = object : Runnable {
        override fun run() {
            if (pressed in 0..3) { send(pressed); h.postDelayed(this, 120) }
        }
    }

    private fun send(z: Int) {
        onKey(when (z) { 0 -> Keys.UP; 1 -> Keys.RIGHT; 2 -> Keys.DOWN; 3 -> Keys.LEFT; else -> Keys.OK })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                val z = zone(e.x, e.y)
                if (z < 0) return false
                pressed = z; haptic(); send(z); invalidate()
                if (z in 0..3) h.postDelayed(repeat, 450)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                h.removeCallbacks(repeat); pressed = -1; invalidate()
            }
        }
        return true
    }
}

/** Panel táctil: deslizar mueve, tocar es OK. */
class TouchPadView(ctx: Context, private val onKey: (Int) -> Unit) : FrameLayout(ctx) {
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var moved = false
    private val step = ctx.dp(42f)

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#17171A"), Color.parseColor("#111113"))).apply {
            cornerRadius = ctx.dp(28f); setStroke(ctx.dpi(1), C.HAIR)
        }
        elevation = ctx.dp(10f)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        val hint = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_tv); setColorFilter(C.DIM) },
                LinearLayout.LayoutParams(ctx.dpi(26), ctx.dpi(26)))
            addView(ctx.label("Desliza para moverte\ntoca para OK", 13f, C.DIM).apply {
                gravity = Gravity.CENTER; setPadding(0, ctx.dpi(8), 0, 0)
            })
        }
        addView(hint, LayoutParams(-2, -2, Gravity.CENTER))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; downTime = e.eventTime; moved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                if (abs(dx) > step || abs(dy) > step) {
                    moved = true
                    val key = if (abs(dx) > abs(dy)) (if (dx > 0) Keys.RIGHT else Keys.LEFT) else (if (dy > 0) Keys.DOWN else Keys.UP)
                    onKey(key); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && e.eventTime - downTime < 350) { haptic(); onKey(Keys.OK) }
            }
        }
        return true
    }
}
