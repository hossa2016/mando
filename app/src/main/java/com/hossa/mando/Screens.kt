package com.hossa.mando

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val mainHandler = Handler(Looper.getMainLooper())

/** Fondo con resplandor cálido. */
fun Modifier.ambient() = this.drawBehind {
    drawRect(M.bgDeep)
    drawRect(
        Brush.radialGradient(
            listOf(M.warm, M.bg, M.bgDeep),
            center = Offset(size.width / 2f, size.height * 0.32f),
            radius = size.width * 1.1f
        )
    )
    drawCircle(
        Brush.radialGradient(listOf(M.gold.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.33f), radius = size.width * 0.55f),
        radius = size.width * 0.55f, center = Offset(size.width / 2f, size.height * 0.33f)
    )
}

// ------------------------------------------------------------------
// Piezas comunes
// ------------------------------------------------------------------
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1B1F), Color(0xFF121214))))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(22.dp)
    ) { content() }
}

@Composable
fun MField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    big: Boolean = false, caps: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Done, onIme: () -> Unit = {}, focus: FocusRequester? = null
) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = M.t(if (big) 26.sp else 17.sp, M.text, big, if (big) 8.sp else 0.sp)
            .copy(textAlign = if (big) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(M.gold),
        keyboardOptions = KeyboardOptions(
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            autoCorrect = false, keyboardType = keyboard, imeAction = ime
        ),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier.let { if (focus != null) it.focusRequester(focus) else it },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(if (big) 64.dp else 52.dp)
                    .clip(shape).background(Color(0xFF0E0E10))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (big) Alignment.Center else Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(hint, style = M.t(if (big) 16.sp else 16.sp, M.dim))
                inner()
            }
        }
    )
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, gold: Boolean = true, onClick: () -> Unit) {
    PressBox(modifier.height(52.dp), shape = RoundedCornerShape(16.dp), gold = gold, onClick = onClick) {
        Text(text, style = M.t(16.sp, if (gold) M.gold else M.text, true))
    }
}

// ------------------------------------------------------------------
// Configuración: buscar Chromecast
// ------------------------------------------------------------------
@Composable
fun SetupScreen(onPaired: () -> Unit) {
    val ctx = LocalContext.current
    val devices = remember { mutableStateListOf<Pair<String, String>>() }
    var pairing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ip by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val d = Discovery(ctx) { name, host, _ -> if (devices.none { it.second == host }) devices.add(name to host) }
        d.start()
        onDispose { d.stop() }
    }

    Column(
        Modifier.fillMaxSize().ambient().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Box(Modifier.size(56.dp).relief(RoundedCornerShape(18.dp), gold = true), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_tv), null, Modifier.size(26.dp), tint = M.gold)
        }
        Spacer(Modifier.height(22.dp))
        Text("Conecta tu\nChromecast", style = M.t(32.sp, M.text, true).copy(lineHeight = 36.sp))
        Spacer(Modifier.height(10.dp))
        Text("Tu celular y el Chromecast deben estar en el mismo WiFi.", style = M.t(15.sp, M.muted).copy(lineHeight = 21.sp))
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = M.gold, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (devices.isEmpty()) "Buscando en tu red…" else "Encontrados", style = M.t(14.sp, M.muted))
        }
        Spacer(Modifier.height(12.dp))
        devices.forEach { (name, host) ->
            PressBox(Modifier.fillMaxWidth().height(72.dp).padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp),
                onClick = { pairing = name to host }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_tv), null, Modifier.size(24.dp), tint = M.icon)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = M.t(17.sp, M.text, true), maxLines = 1)
                        Text(host, style = M.t(13.sp, M.muted))
                    }
                    Text("Conectar", style = M.t(14.sp, M.gold, true))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("¿No aparece? Escribe la IP", style = M.t(14.sp, M.text, true))
        Spacer(Modifier.height(4.dp))
        Text("En el Chromecast: Configuración → Sistema → Información → Estado.", style = M.t(12.sp, M.dim).copy(lineHeight = 17.sp))
        Spacer(Modifier.height(12.dp))
        MField(ip, { ip = it.filter { c -> c.isDigit() || c == '.' } }, "192.168.1.20", keyboard = KeyboardType.Decimal)
        Spacer(Modifier.height(12.dp))
        GoldButton("Conectar", Modifier.fillMaxWidth()) { if (ip.isNotBlank()) pairing = "Chromecast" to ip.trim() }
    }

    pairing?.let { (name, host) ->
        PairingDialog(host, name, onPaired = { pairing = null; onPaired() }, onDismiss = { pairing = null })
    }
}

// ------------------------------------------------------------------
// Emparejamiento
// ------------------------------------------------------------------
@Composable
fun PairingDialog(host: String, name: String, onPaired: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(0) } // 0 conectando, 1 código, 2 error
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Pairing?>(null) }
    var sent by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        val p = Pairing(ctx, host)
        current = p; phase = 0; code = ""; sent = false
        val result = withContext(Dispatchers.IO) { p.run { mainHandler.post { phase = 1 } } }
        when (result) {
            is Pairing.Result.Ok -> { Prefs.save(ctx, host, name); onPaired() }
            is Pairing.Result.WrongCode -> { note = "Ese código no coincide. Te mostramos uno nuevo."; attempt++ }
            is Pairing.Result.Cancelled -> {}
            is Pairing.Result.Error -> { error = result.message; phase = 2 }
        }
    }
    DisposableEffect(Unit) { onDispose { current?.cancel() } }
    LaunchedEffect(phase) { if (phase == 1) { delay(250); try { focus.requestFocus() } catch (e: Exception) { } } }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.9f)) {
            when (phase) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = M.gold, strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Conectando con $name…", style = M.t(18.sp, M.text, true))
                    }
                    note?.let { Spacer(Modifier.height(10.dp)); Text(it, style = M.t(14.sp, M.amber)) }
                }
                1 -> {
                    Text("Escribe el código", style = M.t(22.sp, M.text, true))
                    Spacer(Modifier.height(6.dp))
                    Text((note?.let { "$it\n" } ?: "") + "Aparece en la pantalla de tu TV.", style = M.t(14.sp, M.muted).copy(lineHeight = 20.sp))
                    Spacer(Modifier.height(18.dp))
                    val submit = {
                        if (code.length == 6 && !sent) { sent = true; current?.submitCode(code) }
                    }
                    MField(code, { v ->
                        code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        sent = false
                    }, "······", big = true, caps = true, onIme = submit, focus = focus)
                    Spacer(Modifier.height(16.dp))
                    GoldButton(if (sent) "Vinculando…" else "Vincular", Modifier.fillMaxWidth()) { submit() }
                }
                else -> {
                    Text("No se pudo conectar", style = M.t(20.sp, M.text, true))
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = M.t(14.sp, M.muted).copy(lineHeight = 20.sp))
                    Spacer(Modifier.height(18.dp))
                    GoldButton("Reintentar", Modifier.fillMaxWidth()) { note = null; attempt++ }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Cancelar", Modifier.fillMaxWidth().clickable { current?.cancel(); onDismiss() }.padding(8.dp),
                style = M.t(15.sp, M.muted).copy(textAlign = TextAlign.Center))
        }
    }
}

// ------------------------------------------------------------------
// Control remoto
// ------------------------------------------------------------------
@Composable
fun RemoteScreen(state: TvRemote.State, onChangeDevice: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var touch by remember { mutableStateOf(Prefs.touchMode(ctx)) }
    var menu by remember { mutableStateOf(false) }
    var powerMenu by remember { mutableStateOf(false) }
    var repair by remember { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    var appsVersion by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(10) }
    val host = Prefs.host(ctx) ?: ""
    val name = Prefs.name(ctx)

    LaunchedEffect(hudTick) { if (hudTick > 0) { hud = true; delay(1200); hud = false } }
    LaunchedEffect(state) { if (state == TvRemote.State.NEEDS_PAIRING) repair = true }

    fun vol(d: Int) {
        TvRemote.key(if (d > 0) Keys.VOL_UP else Keys.VOL_DOWN)
        level = (level + d).coerceIn(0, 20); hudTick++
    }

    Box(Modifier.fillMaxSize().ambient()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ringSize: Dp = minOf(240.dp, maxWidth - 90.dp)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Encabezado
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { menu = true }) {
                        val (label, color) = when (state) {
                            TvRemote.State.CONNECTED -> "CONECTADO" to M.green
                            TvRemote.State.CONNECTING -> "CONECTANDO…" to M.amber
                            TvRemote.State.DISCONNECTED -> "SIN CONEXIÓN" to M.red
                            TvRemote.State.NEEDS_PAIRING -> "VINCULAR DE NUEVO" to M.red
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).shadow(6.dp, CircleShape, ambientColor = color, spotColor = color).clip(CircleShape).background(color))
                            Spacer(Modifier.width(7.dp))
                            Text(label, style = M.t(10.sp, color, true, 2.sp))
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(name, style = M.t(20.sp, M.text, true), maxLines = 1)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Reconectar", style = M.t(15.sp)) },
                                onClick = { menu = false; TvRemote.stop(); TvRemote.start(ctx, host) })
                            DropdownMenuItem(text = { Text("Volver a vincular", style = M.t(15.sp)) },
                                onClick = { menu = false; TvRemote.stop(); repair = true })
                            DropdownMenuItem(text = { Text("Cambiar de Chromecast", style = M.t(15.sp)) },
                                onClick = { menu = false; TvRemote.stop(); Prefs.forget(ctx); onChangeDevice() })
                        }
                    }
                    IconButtonR(R.drawable.ic_settings, 44.dp, 19.dp, tint = M.icon) { TvRemote.key(Keys.SETTINGS) }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        IconButtonR(R.drawable.ic_power, 44.dp, 19.dp, gold = true, onLongPress = { powerMenu = true }) {
                            // Apaga/prende la TV por infrarrojo (si está configurado) y el Chromecast por WiFi
                            if (Prefs.irProfile(ctx) > 0) Ir.power(ctx)
                            if (Prefs.tvOff(ctx)) {
                                // Prender: despertar (solo enciende, nunca apaga)
                                TvRemote.key(Keys.WAKEUP)
                                Prefs.setTvOff(ctx, false)
                            } else {
                                TvRemote.key(Prefs.powerKey(ctx))
                                Prefs.setTvOff(ctx, true)
                            }
                        }
                        DropdownMenu(expanded = powerMenu, onDismissRequest = { powerMenu = false }) {
                            if (Ir.available(ctx)) {
                                Text("TV por infrarrojo · apunta el celular a la TV", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = M.t(12.sp, M.gold))
                                Ir.PROFILES.forEachIndexed { i, prof ->
                                    val on = Prefs.irProfile(ctx) == i + 1
                                    DropdownMenuItem(
                                        text = { Text("Probar código ${prof.name}" + if (on) "  ✓" else "", style = M.t(15.sp, if (on) M.gold else M.text)) },
                                        onClick = { Prefs.setIrProfile(ctx, i + 1); Ir.power(ctx, prof); powerMenu = false }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("No usar infrarrojo" + if (Prefs.irProfile(ctx) == 0) "  ✓" else "", style = M.t(15.sp, M.muted)) },
                                    onClick = { Prefs.setIrProfile(ctx, 0); powerMenu = false }
                                )
                            }
                            Text("Chromecast por WiFi", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = M.t(12.sp, M.muted))
                            DropdownMenuItem(
                                text = { Text("Despertar Chromecast", style = M.t(15.sp, M.text)) },
                                onClick = { TvRemote.key(Keys.WAKEUP); Prefs.setTvOff(ctx, false); powerMenu = false }
                            )
                            listOf(Keys.POWER to "Modo 1 · Encendido", Keys.SLEEP to "Modo 2 · Suspender", Keys.TV_POWER to "Modo 3 · Encendido de TV")
                                .forEach { (k, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label + if (Prefs.powerKey(ctx) == k) "  ✓" else "", style = M.t(15.sp, if (Prefs.powerKey(ctx) == k) M.gold else M.text)) },
                                        onClick = { Prefs.setPowerKey(ctx, k); TvRemote.key(k); powerMenu = false }
                                    )
                                }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Segmented("Botones", "Táctil", touch) { touch = it; Prefs.setTouchMode(ctx, it) }
                Spacer(Modifier.height(18.dp))

                Crossfade(touch, animationSpec = tween(280), label = "modo") { t ->
                    Box(Modifier.size(ringSize + 24.dp), contentAlignment = Alignment.Center) {
                        if (t) TouchPad(ringSize) { TvRemote.key(it) } else Dpad(ringSize) { TvRemote.key(it) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                // Botonera
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Labeled("Atrás") { IconButtonR(R.drawable.ic_back, 56.dp) { TvRemote.key(Keys.BACK) } }
                        Labeled("Teclado") { IconButtonR(R.drawable.ic_keyboard, 56.dp) { keyboard = true } }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Labeled("Inicio") { IconButtonR(R.drawable.ic_home, 70.dp, 27.dp, gold = true) { TvRemote.key(Keys.HOME) } }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconButtonR(R.drawable.ic_prev, 42.dp, 17.dp) { TvRemote.key(Keys.PREV) }
                            IconButtonR(R.drawable.ic_play_pause, 42.dp, 17.dp) { TvRemote.key(Keys.PLAY_PAUSE) }
                            IconButtonR(R.drawable.ic_next, 42.dp, 17.dp) { TvRemote.key(Keys.NEXT) }
                        }
                    }
                    Labeled("Volumen") {
                        VolumePill(onUp = { vol(1) }, onDown = { vol(-1) }, onMute = { view.tick(); TvRemote.key(Keys.MUTE) })
                    }
                }

                // Apps
                Spacer(Modifier.height(26.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
                    Text("TUS APPS", Modifier.weight(1f), style = M.t(10.sp, M.muted, true, 2.sp))
                    Text("Mantén para cambiar", style = M.t(11.sp, M.dim))
                }
                Spacer(Modifier.height(10.dp))
                key(appsVersion) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        (0 until Prefs.SLOTS).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                pair.forEach { slot ->
                                    val app = Prefs.app(ctx, slot)
                                    AppTile(app, Modifier.weight(1f),
                                        onClick = { if (app != null) TvRemote.open(ctx, app.link) { msg -> toast = msg } else pickSlot = slot },
                                        onLongPress = { pickSlot = slot })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // Indicador de volumen
        AnimatedVisibility(
            visible = hud,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp),
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 3 }
        ) {
            val shape = RoundedCornerShape(18.dp)
            val fill by animateFloatAsState(level / 20f, tween(180), label = "vol")
            Row(
                Modifier.width(220.dp).shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape).background(Color(0xF0181A1E)).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_volume), null, Modifier.size(18.dp), tint = M.gold)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(fill).height(4.dp).background(Brush.horizontalGradient(listOf(M.goldDark, M.goldLight))))
                }
            }
        }
    }

    toast?.let { msg ->
        Dialog(onDismissRequest = { toast = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(Modifier.fillMaxWidth(0.9f)) {
                Text("No se pudo abrir la app", style = M.t(19.sp, M.text, true))
                Spacer(Modifier.height(8.dp))
                Text(msg, style = M.t(14.sp, M.muted).copy(lineHeight = 20.sp))
                Spacer(Modifier.height(16.dp))
                GoldButton("Entendido", Modifier.fillMaxWidth()) { toast = null }
            }
        }
    }
    if (repair) PairingDialog(host, name, onPaired = { repair = false; TvRemote.start(ctx, host) }, onDismiss = { repair = false })
    if (keyboard) KeyboardDialog { keyboard = false }
    if (pickSlot >= 0) AppPickerDialog(pickSlot, onDone = { pickSlot = -1; appsVersion++ })
}

@Composable
private fun key(k: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

// ------------------------------------------------------------------
// Teclado
// ------------------------------------------------------------------
@Composable
fun KeyboardDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); try { focus.requestFocus() } catch (e: Exception) { } }
    val send = { if (text.isNotEmpty()) { TvRemote.type(text); text = "" } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.92f)) {
            Text("Escribir en la TV", style = M.t(20.sp, M.text, true))
            Spacer(Modifier.height(4.dp))
            Text("Primero selecciona el buscador en la TV.", style = M.t(13.sp, M.muted))
            Spacer(Modifier.height(16.dp))
            MField(text, { text = it }, "Escribe aquí", ime = ImeAction.Send, onIme = { send() }, focus = focus)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PressBox(Modifier.width(58.dp).height(52.dp), shape = RoundedCornerShape(16.dp), repeat = true,
                    onClick = { TvRemote.key(Keys.DEL) }) {
                    Icon(painterResource(R.drawable.ic_backspace), null, Modifier.size(20.dp), tint = M.text)
                }
                GoldButton("Enviar", Modifier.weight(1f), gold = false) { send() }
                GoldButton("Buscar", Modifier.weight(1f)) { send(); TvRemote.key(Keys.ENTER) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Cerrar", Modifier.fillMaxWidth().clickable { onClose() }.padding(8.dp),
                style = M.t(15.sp, M.muted).copy(textAlign = TextAlign.Center))
        }
    }
}

// ------------------------------------------------------------------
// Elegir app de un espacio
// ------------------------------------------------------------------
@Composable
fun AppPickerDialog(slot: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 lista, 1 paquete, 2 enlace, 3 ADB
    var adbApps by remember { mutableStateOf<List<String>?>(null) }
    var adbError by remember { mutableStateOf<String?>(null) }
    var needsPair by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var connPort by remember { mutableStateOf("") }
    var pairBusy by remember { mutableStateOf(false) }
    var pairMsg by remember { mutableStateOf<String?>(null) }
    var adbLoad by remember { mutableIntStateOf(0) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var method by remember { mutableIntStateOf(1) }
    fun pkgLink(m: Int, p: String) = if (m == 1) "market://launch?id=$p"
        else "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=$p;end"

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.92f)) {
            Text("Espacio ${slot + 1}", style = M.t(22.sp, M.text, true))
            Spacer(Modifier.height(14.dp))
            when (mode) {
                0 -> {
                    val items = listOf<TvApp?>(null) + Prefs.CATALOG.map<TvApp, TvApp?> { it } + listOf(null, null, null)
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(items) { i, app ->
                            val extra = if (i == 0) -1 else i - 1 - Prefs.CATALOG.size
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    when {
                                        app != null -> { Prefs.setApp(ctx, slot, app); onDone() }
                                        extra == -1 -> { mode = 3; adbLoad++ }
                                        extra == 0 -> mode = 1
                                        extra == 1 -> mode = 2
                                        else -> { Prefs.setApp(ctx, slot, null); onDone() }
                                    }
                                }.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app != null) {
                                    val tint = Color(app.tint())
                                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center) {
                                        Text(app.glyph, style = M.t(if (app.glyph.length > 1) 11.sp else 14.sp, tint, true))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.name, style = M.t(16.sp))
                                } else {
                                    Text(when (extra) { -1 -> "★  Apps instaladas en el Chromecast (ADB)"; 0 -> "Otra app (por nombre de paquete)…"; 1 -> "Otra (escribir enlace)…"; else -> "Dejar vacío" },
                                        style = M.t(15.sp, if (extra == 2) M.muted else M.gold, extra == -1))
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Text("Para apps sin enlace público, como Xuper TV. Toca Probar 1 y mira la TV; si no abre, Probar 2. Se guarda el último que probaste.",
                        style = M.t(13.sp, M.muted).copy(lineHeight = 18.sp))
                    Spacer(Modifier.height(12.dp))
                    MField(name, { name = it }, "Nombre, por ejemplo Xuper TV")
                    Spacer(Modifier.height(10.dp))
                    MField(value, { value = it.trim() }, "Paquete, por ejemplo com.ejemplo.tv", keyboard = KeyboardType.Uri)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Probar 1", Modifier.weight(1f), gold = method == 1) {
                            if (value.isNotEmpty()) { method = 1; TvRemote.openApp(pkgLink(1, value)) }
                        }
                        GoldButton("Probar 2", Modifier.weight(1f), gold = method == 2) {
                            if (value.isNotEmpty()) { method = 2; TvRemote.openApp(pkgLink(2, value)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Guardar", Modifier.fillMaxWidth()) {
                        if (name.isNotBlank() && value.isNotBlank()) { Prefs.setApp(ctx, slot, TvApp(name.trim(), pkgLink(method, value))); onDone() }
                    }
                }
                3 -> {
                    LaunchedEffect(adbLoad) {
                        adbApps = null; adbError = null; needsPair = false
                        try { adbApps = withContext(Dispatchers.IO) { Adb.apps(ctx) } }
                        catch (e: Adb.NeedsPairing) { needsPair = true; pairMsg = null }
                        catch (e: Exception) { adbError = e.message ?: "Error de ADB" }
                    }
                    // Buscar los puertos solos mientras se muestra el panel de vinculación
                    LaunchedEffect(needsPair) {
                        while (needsPair) {
                            if (pairPort.isEmpty()) withContext(Dispatchers.IO) { Adb.discoverPort(ctx, "_adb-tls-pairing._tcp", 4000) }?.let { pairPort = it.toString() }
                            if (connPort.isEmpty()) withContext(Dispatchers.IO) { Adb.discoverPort(ctx, "_adb-tls-connect._tcp", 3000) }?.let { connPort = it.toString() }
                            delay(1500)
                        }
                    }
                    if (needsPair) {
                        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                            Text("Vincular depuración inalámbrica", style = M.t(16.sp, M.gold, true))
                            Spacer(Modifier.height(6.dp))
                            Text("En el Chromecast: Opciones para desarrolladores → Depuración inalámbrica (actívala) → Vincular dispositivo con código de vinculación. Escribe aquí el código que aparece.",
                                style = M.t(13.sp, M.muted).copy(lineHeight = 18.sp))
                            Spacer(Modifier.height(12.dp))
                            MField(pairCode, { pairCode = it.filter { c -> c.isDigit() }.take(6) }, "Código de 6 dígitos", big = true, keyboard = KeyboardType.Number)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Puerto de vinculación", style = M.t(11.sp, M.dim))
                                    MField(pairPort, { pairPort = it.filter { c -> c.isDigit() }.take(5) }, "Buscando…", keyboard = KeyboardType.Number)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Puerto de conexión", style = M.t(11.sp, M.dim))
                                    MField(connPort, { connPort = it.filter { c -> c.isDigit() }.take(5) }, "Buscando…", keyboard = KeyboardType.Number)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Los puertos salen solos. Si no, cópialos de la TV: el de vinculación está junto al código; el de conexión en \"Dirección IP y puerto\".",
                                style = M.t(11.sp, M.dim).copy(lineHeight = 15.sp))
                            pairMsg?.let { Spacer(Modifier.height(8.dp)); Text(it, style = M.t(13.sp, M.amber).copy(lineHeight = 18.sp)) }
                            Spacer(Modifier.height(12.dp))
                            GoldButton(if (pairBusy) "Vinculando…" else "Vincular", Modifier.fillMaxWidth()) {
                                val pp = pairPort.toIntOrNull()
                                if (pairBusy) return@GoldButton
                                if (pairCode.length != 6 || pp == null) { pairMsg = "Falta el código o el puerto de vinculación."; return@GoldButton }
                                pairBusy = true; pairMsg = null
                                Thread {
                                    val err = try {
                                        Adb.pair(ctx, pp, pairCode)
                                        connPort.toIntOrNull()?.let { Adb.setPort(ctx, it) }
                                        null
                                    } catch (e: Exception) { e.message ?: "Error" }
                                    mainHandler.post {
                                        pairBusy = false
                                        if (err == null) { needsPair = false; pairCode = ""; adbLoad++ } else pairMsg = err
                                    }
                                }.start()
                            }
                        }
                    } else if (value.isEmpty()) {
                        when {
                            adbError != null -> {
                                Text(adbError ?: "", style = M.t(14.sp, M.muted).copy(lineHeight = 20.sp))
                                Spacer(Modifier.height(14.dp))
                                GoldButton("Reintentar", Modifier.fillMaxWidth()) { adbLoad++ }
                            }
                            adbApps == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = M.gold, strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Leyendo apps del Chromecast… Si la TV pregunta por la depuración, acepta.",
                                    style = M.t(14.sp, M.muted).copy(lineHeight = 20.sp))
                            }
                            else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                itemsIndexed(adbApps ?: emptyList()) { _, pkg ->
                                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .clickable { value = pkg; name = Adb.prettyName(pkg) }
                                        .padding(horizontal = 8.dp, vertical = 9.dp)) {
                                        Text(Adb.prettyName(pkg), style = M.t(16.sp))
                                        Text(pkg, style = M.t(12.sp, M.dim))
                                    }
                                }
                            }
                        }
                    } else {
                        Text(value, style = M.t(13.sp, M.dim))
                        Spacer(Modifier.height(10.dp))
                        MField(name, { name = it }, "Nombre para el botón")
                        testMsg?.let { Spacer(Modifier.height(8.dp)); Text(it, style = M.t(13.sp, M.amber).copy(lineHeight = 18.sp)) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoldButton("Probar", Modifier.weight(1f), gold = false) {
                                testMsg = null
                                TvRemote.open(ctx, "adb:$value") { testMsg = it }
                            }
                            GoldButton("Guardar", Modifier.weight(1f)) {
                                if (name.isNotBlank()) { Prefs.setApp(ctx, slot, TvApp(name.trim(), "adb:$value")); onDone() }
                            }
                        }
                    }
                }
                else -> {
                    MField(name, { name = it }, "Nombre de la app")
                    Spacer(Modifier.height(10.dp))
                    MField(value, { value = it.trim() }, "Enlace, por ejemplo https://…", keyboard = KeyboardType.Uri)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Probar", Modifier.weight(1f), gold = false) { if (value.isNotEmpty()) TvRemote.openApp(value) }
                        GoldButton("Guardar", Modifier.weight(1f)) {
                            if (name.isNotBlank() && value.isNotBlank()) { Prefs.setApp(ctx, slot, TvApp(name.trim(), value)); onDone() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (mode == 0) "Cerrar" else "Volver", Modifier.fillMaxWidth().clickable {
                    if (mode == 0) onDone() else if (mode == 3 && value.isNotEmpty()) { value = ""; name = "" } else { mode = 0; value = ""; name = "" }
                }.padding(8.dp),
                style = M.t(15.sp, M.muted).copy(textAlign = TextAlign.Center))
        }
    }
}
