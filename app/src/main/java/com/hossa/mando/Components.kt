package com.hossa.mando

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// ------------------------------------------------------------------
// Paleta y tipografía
// ------------------------------------------------------------------
object M {
    val bg = Color(0xFF0C0C0E)
    val bgDeep = Color(0xFF09090A)
    val warm = Color(0xFF1A1814)
    val top = Color(0xFF232327)
    val bottom = Color(0xFF141416)
    val card = Color(0xFF151518)
    val text = Color(0xFFEEEAE3)
    val icon = Color(0xFFB9B4AC)
    val muted = Color(0xFF8A867F)
    val dim = Color(0xFF55524D)
    val gold = Color(0xFFD9B77E)
    val goldLight = Color(0xFFE8CC98)
    val goldDark = Color(0xFFB8935A)
    val green = Color(0xFF7FB38A)
    val amber = Color(0xFFD9A441)
    val red = Color(0xFFE0605A)
    val hair = Color(0x14FFFFFF)

    val font = FontFamily(Font(R.font.barlow_regular, FontWeight.Normal), Font(R.font.barlow_medium, FontWeight.Medium))

    fun t(size: TextUnit, color: Color = text, medium: Boolean = false, spacing: TextUnit = 0.sp) =
        TextStyle(fontFamily = font, fontSize = size, color = color,
            fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal, letterSpacing = spacing)
}

fun View.tick() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
fun View.soft() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

/** Fondo y borde de los botones con relieve. */
fun Modifier.relief(shape: Shape, gold: Boolean = false, elevation: Dp = 10.dp) = this
    .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(M.top, M.bottom)))
    .border(1.dp, if (gold) M.gold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), shape)

/**
 * Botón con relieve que se hunde con rebote.
 * Si [repeat] es true, la acción se repite mientras se mantiene presionado.
 */
@Composable
fun PressBox(
    modifier: Modifier,
    shape: Shape = CircleShape,
    gold: Boolean = false,
    repeat: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium), label = "press"
    )
    Box(
        modifier
            .scale(scale)
            .relief(shape, gold)
            .pointerInput(repeat, onLongPress) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        var job: Job? = null
                        if (repeat) {
                            view.tick(); onClick()
                            job = scope.launch {
                                delay(420)
                                while (true) { onClick(); view.soft(); delay(140) }
                            }
                        }
                        tryAwaitRelease()
                        job?.cancel()
                        pressed = false
                    },
                    onTap = { if (!repeat) { view.tick(); onClick() } },
                    onLongPress = onLongPress?.let { lp -> { _: Offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); lp()
                    } }
                )
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun IconButtonR(icon: Int, size: Dp, iconSize: Dp = 22.dp, gold: Boolean = false, tint: Color = M.text, onClick: () -> Unit) {
    PressBox(Modifier.size(size), gold = gold, onClick = onClick) {
        Icon(painterResource(icon), null, Modifier.size(iconSize), tint = if (gold) M.gold else tint)
    }
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(7.dp))
        Text(label, style = M.t(11.sp, M.muted))
    }
}

// ------------------------------------------------------------------
// Selector deslizante
// ------------------------------------------------------------------
@Composable
fun Segmented(left: String, right: String, rightOn: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    val width = 200.dp
    val knob by animateDpAsState(if (rightOn) width / 2 - 3.dp else 0.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "seg")
    Box(
        Modifier.width(width).height(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC141417))
            .border(1.dp, M.hair, RoundedCornerShape(14.dp))
            .padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = knob).width(width / 2 - 3.dp).fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2F), Color(0xFF1F1F23))))
        )
        Row(Modifier.fillMaxSize()) {
            listOf(left to false, right to true).forEach { (label, isRight) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().pointerInput(isRight) {
                        detectTapGestures { view.tick(); onChange(isRight) }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = M.t(13.sp, if (rightOn == isRight) M.text else M.muted, medium = true))
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Anillo direccional
// ------------------------------------------------------------------
@Composable
fun Dpad(diameter: Dp, onKey: (Int) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var zone by remember { mutableIntStateOf(-1) }      // sector iluminado
    val glow = remember { Animatable(0f) }
    var okDown by remember { mutableStateOf(false) }
    val okScale by animateFloatAsState(if (okDown) 0.92f else 1f, spring(dampingRatio = 0.4f), label = "ok")
    val okGlow by animateFloatAsState(if (okDown) 1f else 0f, tween(if (okDown) 80 else 400), label = "okg")

    fun keyFor(z: Int) = when (z) { 0 -> Keys.UP; 1 -> Keys.RIGHT; 2 -> Keys.DOWN; 3 -> Keys.LEFT; else -> Keys.OK }

    Box(
        Modifier.size(diameter)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .pointerInput(Unit) {
                val boxW = this.size.width.toFloat()
                val boxH = this.size.height.toFloat()
                detectTapGestures(onPress = { p ->
                    val c = Offset(boxW / 2f, boxH / 2f)
                    val d = p - c
                    val r = boxW / 2f
                    val dist = hypot(d.x, d.y)
                    val z = if (dist > r) -1 else if (dist < r * 0.42f) 4 else {
                        val ang = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble()))
                        when { ang >= -135 && ang < -45 -> 0; ang >= -45 && ang < 45 -> 1; ang >= 45 && ang < 135 -> 2; else -> 3 }
                    }
                    var job: Job? = null
                    if (z >= 0) { view.tick(); onKey(keyFor(z)) }
                    if (z == 4) okDown = true else if (z >= 0) {
                        zone = z
                        scope.launch { glow.snapTo(1f) }
                        job = scope.launch {
                            delay(450)
                            while (true) { onKey(keyFor(z)); view.soft(); delay(120) }
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                    okDown = false
                    scope.launch { glow.animateTo(0f, tween(450)) }
                })
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            // Base del anillo
            drawCircle(Brush.sweepGradient(listOf(Color(0xFF1C1C20), Color(0xFF121214), Color(0xFF1C1C20), Color(0xFF121214), Color(0xFF1C1C20)), c), r)
            drawCircle(Brush.verticalGradient(listOf(Color(0x12FFFFFF), Color.Transparent), 0f, r), r)
            drawCircle(Color.White.copy(alpha = 0.07f), r - 0.5f, style = Stroke(1.dp.toPx()))
            // Sector iluminado
            if (zone in 0..3 && glow.value > 0f) {
                val start = -135f + zone * 90f
                drawArc(
                    Brush.radialGradient(listOf(M.gold.copy(alpha = 0.30f * glow.value), M.gold.copy(alpha = 0.06f * glow.value)), c, r),
                    start, 90f, true
                )
            }
            // Flechas
            val okR = r * 0.42f
            val mid = (r + okR) / 2f
            val s = 8.dp.toPx()
            for (i in 0..3) {
                val col = if (zone == i && glow.value > 0.3f) M.goldLight else M.icon
                val p = Path()
                when (i) {
                    0 -> { p.moveTo(c.x - s, c.y - mid + s * .5f); p.lineTo(c.x, c.y - mid - s * .5f); p.lineTo(c.x + s, c.y - mid + s * .5f) }
                    1 -> { p.moveTo(c.x + mid - s * .5f, c.y - s); p.lineTo(c.x + mid + s * .5f, c.y); p.lineTo(c.x + mid - s * .5f, c.y + s) }
                    2 -> { p.moveTo(c.x - s, c.y + mid - s * .5f); p.lineTo(c.x, c.y + mid + s * .5f); p.lineTo(c.x + s, c.y + mid - s * .5f) }
                    3 -> { p.moveTo(c.x - mid + s * .5f, c.y - s); p.lineTo(c.x - mid - s * .5f, c.y); p.lineTo(c.x - mid + s * .5f, c.y + s) }
                }
                drawPath(p, col, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Anillo de separación y botón OK
            drawCircle(Color.Black.copy(alpha = 0.25f), okR + 6.dp.toPx())
            val or = okR * okScale
            if (okGlow > 0f) drawCircle(M.gold.copy(alpha = 0.28f * okGlow), or + 10.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2A2F), Color(0xFF151518)), Offset(c.x, c.y - or * 0.4f), or * 1.3f), or)
            drawCircle(M.gold.copy(alpha = 0.45f + 0.4f * okGlow), or, style = Stroke(1.2.dp.toPx()))
        }
        Text("OK", Modifier.align(Alignment.Center).scale(okScale), style = M.t(16.sp, M.gold, true, 3.sp))
    }
}

// ------------------------------------------------------------------
// Panel táctil con onda dorada
// ------------------------------------------------------------------
@Composable
fun TouchPad(size: Dp, onKey: (Int) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var rippleAt by remember { mutableStateOf(Offset.Unspecified) }
    val ripple = remember { Animatable(1f) }
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.size(size)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE61C1C20), Color(0xE6101012))))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    view.tick(); onKey(Keys.OK)
                    rippleAt = p
                    scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(600)) }
                }
            }
            .pointerInput(Unit) {
                val step = 42.dp.toPx()
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                    change.consume()
                    acc += drag
                    if (abs(acc.x) > step || abs(acc.y) > step) {
                        val key = if (abs(acc.x) > abs(acc.y)) (if (acc.x > 0) Keys.RIGHT else Keys.LEFT)
                            else (if (acc.y > 0) Keys.DOWN else Keys.UP)
                        onKey(key); view.soft()
                        acc = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (rippleAt != Offset.Unspecified && ripple.value < 1f) {
                drawCircle(M.gold.copy(alpha = 0.35f * (1f - ripple.value)), 10.dp.toPx() + 170.dp.toPx() * ripple.value, rippleAt)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_touch), null, Modifier.size(28.dp), tint = Color(0xFF5F5B55))
            Spacer(Modifier.height(8.dp))
            Text("Desliza o toca", style = M.t(12.sp, Color(0xFF5F5B55)))
        }
    }
}

// ------------------------------------------------------------------
// Volumen y apps
// ------------------------------------------------------------------
@Composable
fun VolumePill(onUp: () -> Unit, onDown: () -> Unit, onMute: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.width(60.dp).height(138.dp).relief(shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        HoldText("+", onUp)
        Box(Modifier.size(30.dp).pointerInput(Unit) { detectTapGestures { onMute() } }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_volume), null, Modifier.size(16.dp), tint = Color(0xFF7A766F))
        }
        HoldText("−", onDown)
    }
}

@Composable
private fun HoldText(label: String, action: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.8f else 1f, spring(dampingRatio = 0.45f), label = "h")
    Box(
        Modifier.fillMaxWidth().height(48.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true; view.tick(); action()
                val job = scope.launch { delay(420); while (true) { action(); view.soft(); delay(140) } }
                tryAwaitRelease(); job.cancel(); pressed = false
            })
        },
        contentAlignment = Alignment.Center
    ) { Text(label, Modifier.scale(sc), style = M.t(26.sp)) }
}

@Composable
fun AppTile(app: TvApp?, modifier: Modifier, onClick: () -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    PressBox(modifier.height(52.dp), shape = shape, onClick = onClick, onLongPress = onLongPress) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app == null) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, M.dim, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center) { Text("+", style = M.t(16.sp, M.muted)) }
                Spacer(Modifier.width(10.dp))
                Text("Agregar", style = M.t(14.sp, M.muted))
            } else {
                val tint = Color(app.tint())
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center) {
                    Text(app.glyph, style = M.t(if (app.glyph.length > 1) 11.sp else 14.sp, tint, true))
                }
                Spacer(Modifier.width(10.dp))
                Text(app.name, style = M.t(14.sp, Color(0xFFDCD7CF)), maxLines = 1)
            }
        }
    }
}

