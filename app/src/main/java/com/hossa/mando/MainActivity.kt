package com.hossa.mando

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var discovery: Discovery? = null
    private var statusText: TextView? = null
    private var statusDot: View? = null
    private var onRemote = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.host(this) != null) showRemote() else showSetup()
    }

    override fun onResume() {
        super.onResume()
        if (onRemote) Prefs.host(this)?.let { TvRemote.start(this, it) }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) TvRemote.stop()
    }

    override fun onDestroy() {
        discovery?.stop()
        super.onDestroy()
    }

    // ==================================================================
    // Configuración inicial: buscar y emparejar
    // ==================================================================
    private fun showSetup(message: String? = null) {
        onRemote = false
        TvRemote.listener = null
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24), dpi(56), dpi(24), dpi(32))
        }
        col.addView(ImageView(this).apply { setImageResource(R.drawable.ic_tv); setColorFilter(C.GOLD) },
            LinearLayout.LayoutParams(dpi(36), dpi(36)))
        col.addView(label("Conecta tu Chromecast", 28f, C.TEXT, true).apply { setPadding(0, dpi(16), 0, 0) })
        col.addView(label(message ?: "Tu celular y el Chromecast deben estar conectados al mismo WiFi.", 15f, C.MUTED).apply {
            setPadding(0, dpi(8), 0, dpi(24)); setLineSpacing(0f, 1.2f)
        })

        val searching = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ProgressBar(this@MainActivity).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(C.GOLD) },
                LinearLayout.LayoutParams(dpi(18), dpi(18)))
            addView(label("Buscando en tu red…", 14f, C.MUTED).apply { setPadding(dpi(10), 0, 0, 0) })
        }
        col.addView(searching)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(12), 0, 0) }
        col.addView(list)
        val seen = HashSet<String>()

        discovery?.stop()
        discovery = Discovery(this) { name, host ->
            if (seen.add(host)) {
                list.addView(deviceCard(name, host), LinearLayout.LayoutParams(-1, dpi(68)).apply { bottomMargin = dpi(10) })
            }
        }.also { it.start() }

        col.addView(label("¿No aparece? Escribe la IP", 13f, C.MUTED).apply { setPadding(0, dpi(28), 0, dpi(8)) })
        col.addView(label("En el Chromecast: Configuración → Sistema → Información → Estado → Dirección IP.", 12f, C.DIM).apply {
            setPadding(0, 0, 0, dpi(10)); setLineSpacing(0f, 1.15f)
        })
        val ip = field("192.168.1.20").apply { inputType = InputType.TYPE_CLASS_PHONE }
        col.addView(ip, LinearLayout.LayoutParams(-1, dpi(50)))
        col.addView(softButton(text = "Conectar", oval = false, gold = true).apply {
            setOnClickListener {
                val h = ip.text.toString().trim()
                if (h.isNotEmpty()) startPairing(h, "Chromecast")
            }
        }, LinearLayout.LayoutParams(-1, dpi(52)).apply { topMargin = dpi(12) })

        setContentView(ScrollView(this).apply { setBackgroundColor(C.BG); addView(col) })
    }

    private fun deviceCard(name: String, host: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(16), 0, dpi(16), 0)
        background = GradientDrawable().apply { setColor(C.SURFACE); cornerRadius = dp(16f); setStroke(dpi(1), C.HAIR) }
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_tv); setColorFilter(C.ICON) },
            LinearLayout.LayoutParams(dpi(24), dpi(24)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14), 0, 0, 0)
            addView(label(name, 17f, C.TEXT, true))
            addView(label(host, 13f, C.MUTED))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label("Conectar", 14f, C.GOLD, true))
        isClickable = true
        setOnClickListener { haptic(); startPairing(host, name) }
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(C.DIM)
        setTextColor(C.TEXT)
        typeface = font()
        textSize = 17f
        setSingleLine()
        setPadding(dpi(16), 0, dpi(16), 0)
        background = GradientDrawable().apply { setColor(Color.parseColor("#0F0F11")); cornerRadius = dp(14f); setStroke(dpi(1), Color.parseColor("#22FFFFFF")) }
    }

    private fun startPairing(host: String, name: String) {
        discovery?.stop()
        val dialog = Dialog(this, R.style.Theme_Mando_Dialog)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24), dpi(24), dpi(24), dpi(20))
            background = GradientDrawable().apply { setColor(Color.parseColor("#151518")); cornerRadius = dp(24f); setStroke(dpi(1), C.HAIR) }
        }
        val title = label("Conectando con $name…", 20f, C.TEXT, true)
        val info = label("Esto toma unos segundos.", 14f, C.MUTED).apply { setPadding(0, dpi(8), 0, dpi(16)); setLineSpacing(0f, 1.2f) }
        val code = field("Código de 6 caracteres").apply {
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(6))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
            textSize = 24f
            visibility = View.GONE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val ok = softButton(text = "Vincular", oval = false, gold = true).apply { visibility = View.GONE }
        val cancel = label("Cancelar", 15f, C.MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dpi(16), 0, dpi(4)) }
        card.addView(title); card.addView(info)
        card.addView(code, LinearLayout.LayoutParams(-1, dpi(60)))
        card.addView(ok, LinearLayout.LayoutParams(-1, dpi(52)).apply { topMargin = dpi(14) })
        card.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(false)
        dialog.show()

        var pairing: Pairing? = null
        fun attempt(note: String?) {
            val p = Pairing(this, host)
            pairing = p
            title.text = "Conectando con $name…"
            info.text = note ?: "Esto toma unos segundos."
            code.visibility = View.GONE; ok.visibility = View.GONE
            Thread {
                val result = p.run {
                    main.post {
                        title.text = "Escribe el código"
                        info.text = (if (note != null) "$note\n" else "") + "Aparece en la pantalla de tu TV."
                        code.setText("")
                        code.visibility = View.VISIBLE; ok.visibility = View.VISIBLE
                        code.requestFocus()
                        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    }
                }
                main.post {
                    when (result) {
                        is Pairing.Result.Ok -> {
                            Prefs.save(this, host, name)
                            dialog.dismiss()
                            Toast.makeText(this, "Listo, conectado a $name", Toast.LENGTH_SHORT).show()
                            showRemote()
                        }
                        is Pairing.Result.WrongCode -> attempt("Ese código no coincide. Te mostramos uno nuevo.")
                        is Pairing.Result.Cancelled -> dialog.dismiss()
                        is Pairing.Result.Error -> {
                            title.text = "No se pudo conectar"
                            info.text = result.message
                            code.visibility = View.GONE
                            ok.visibility = View.VISIBLE
                            (ok.getChildAt(0) as? TextView)?.text = "Reintentar"
                            ok.setOnClickListener {
                                (ok.getChildAt(0) as? TextView)?.text = "Vincular"
                                ok.setOnClickListener { submit(code, pairing) }
                                attempt(null)
                            }
                        }
                    }
                }
            }.start()
        }
        ok.setOnClickListener { submit(code, pairing) }
        code.setOnEditorActionListener { _, _, _ -> submit(code, pairing); true }
        cancel.setOnClickListener {
            pairing?.cancel()
            dialog.dismiss()
            if (!onRemote) showSetup()
        }
        attempt(null)
    }

    private fun submit(code: EditText, p: Pairing?) {
        val c = code.text.toString().trim()
        if (c.length != 6) {
            Toast.makeText(this, "El código tiene 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        code.isEnabled = false
        main.postDelayed({ code.isEnabled = true }, 1500)
        p?.submitCode(c)
    }

    // ==================================================================
    // Control remoto
    // ==================================================================
    private fun showRemote() {
        onRemote = true
        discovery?.stop()
        val host = Prefs.host(this) ?: return showSetup()
        val name = Prefs.name(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(40), dpi(20), dpi(24))
            clipChildren = false; clipToPadding = false
        }

        // Encabezado
        val dot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(C.AMBER) } }
        val status = label("CONECTANDO…", 11f, C.MUTED).apply { letterSpacing = 0.12f }
        statusDot = dot; statusText = status
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(name, 19f, C.TEXT, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(dot, LinearLayout.LayoutParams(dpi(8), dpi(8)).apply { marginStart = dpi(10) })
                })
                setOnClickListener { deviceMenu() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(softButton(R.drawable.ic_settings, sizeDp = 44, iconDp = 20, tint = C.ICON).apply {
                setOnClickListener { haptic(); TvRemote.key(Keys.SETTINGS) }
            }, LinearLayout.LayoutParams(dpi(44), dpi(44)).apply { marginEnd = dpi(12) })
            addView(softButton(R.drawable.ic_power, sizeDp = 44, iconDp = 20, gold = true).apply {
                setOnClickListener { haptic(); TvRemote.key(Keys.POWER) }
            }, LinearLayout.LayoutParams(dpi(44), dpi(44)))
        }
        col.addView(header)

        // Selector Botones / Táctil
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(3), dpi(3), dpi(3), dpi(3))
            background = GradientDrawable().apply { setColor(C.RING); cornerRadius = dp(13f); setStroke(dpi(1), C.HAIR) }
        }
        val segA = label("Botones", 13f, C.MUTED, true).apply { gravity = Gravity.CENTER }
        val segB = label("Táctil", 13f, C.MUTED, true).apply { gravity = Gravity.CENTER }
        seg.addView(segA, LinearLayout.LayoutParams(0, dpi(32), 1f))
        seg.addView(segB, LinearLayout.LayoutParams(0, dpi(32), 1f))
        col.addView(seg, LinearLayout.LayoutParams(dpi(200), -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpi(22) })

        val area = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        val dpad = DpadView(this) { TvRemote.key(it) }
        val pad = TouchPadView(this) { TvRemote.key(it) }
        val side = minOf(dpi(250), resources.displayMetrics.widthPixels - dpi(80))
        area.addView(dpad, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
        area.addView(pad, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
        col.addView(area, LinearLayout.LayoutParams(-1, side + dpi(24)).apply { topMargin = dpi(14) })

        fun setMode(touch: Boolean) {
            Prefs.setTouchMode(this, touch)
            dpad.visibility = if (touch) View.GONE else View.VISIBLE
            pad.visibility = if (touch) View.VISIBLE else View.GONE
            val on = GradientDrawable().apply { setColor(Color.parseColor("#232327")); cornerRadius = dp(10f) }
            segA.background = if (!touch) on else null
            segB.background = if (touch) on else null
            segA.setTextColor(if (!touch) C.TEXT else C.MUTED)
            segB.setTextColor(if (touch) C.TEXT else C.MUTED)
        }
        segA.setOnClickListener { it.haptic(); setMode(false) }
        segB.setOnClickListener { it.haptic(); setMode(true) }
        setMode(Prefs.touchMode(this))

        // Botonera
        fun withLabel(v: View, size: Int, text: String) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(v, LinearLayout.LayoutParams(dpi(size), dpi(size)))
            addView(label(text, 11f, C.MUTED).apply { setPadding(0, dpi(6), 0, 0) })
        }
        fun key(icon: Int, code: Int, size: Int = 54, gold: Boolean = false, iconDp: Int = 22) =
            softButton(icon, sizeDp = size, iconDp = iconDp, gold = gold).apply {
                setOnClickListener { haptic(); TvRemote.key(code) }
            }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(withLabel(key(R.drawable.ic_back, Keys.BACK), 54, "Atrás"))
            addView(withLabel(softButton(R.drawable.ic_keyboard, sizeDp = 54).apply {
                setOnClickListener { haptic(); keyboard() }
            }, 54, "Teclado"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14) })
        }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(withLabel(key(R.drawable.ic_home, Keys.HOME, 66, gold = true, iconDp = 26), 66, "Inicio"))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                addView(key(R.drawable.ic_prev, Keys.PREV, 42, iconDp = 18), LinearLayout.LayoutParams(dpi(42), dpi(42)))
                addView(key(R.drawable.ic_play_pause, Keys.PLAY_PAUSE, 42, iconDp = 18), LinearLayout.LayoutParams(dpi(42), dpi(42)).apply { marginStart = dpi(10) })
                addView(key(R.drawable.ic_next, Keys.NEXT, 42, iconDp = 18), LinearLayout.LayoutParams(dpi(42), dpi(42)).apply { marginStart = dpi(10) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14) })
        }
        val volume = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.SURFACE_TOP, C.SURFACE_BOTTOM)).apply {
                cornerRadius = dp(30f); setStroke(dpi(1), C.HAIR)
            }
            elevation = dp(6f)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            val plus = label("+", 26f, C.TEXT).apply { gravity = Gravity.CENTER; repeatWhileHeld { TvRemote.key(Keys.VOL_UP) } }
            val minus = label("−", 26f, C.TEXT).apply { gravity = Gravity.CENTER; repeatWhileHeld { TvRemote.key(Keys.VOL_DOWN) } }
            val mute = ImageView(context).apply {
                setImageResource(R.drawable.ic_volume); setColorFilter(C.MUTED)
                setPadding(dpi(4), dpi(4), dpi(4), dpi(4))
                setOnClickListener { haptic(); TvRemote.key(Keys.MUTE) }
            }
            addView(plus, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(mute, LinearLayout.LayoutParams(dpi(30), dpi(30)))
            addView(minus, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(volume, LinearLayout.LayoutParams(dpi(60), dpi(140)))
            addView(label("Volumen", 11f, C.MUTED).apply { setPadding(0, dpi(6), 0, 0) })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            clipChildren = false
            addView(left)
            addView(center, LinearLayout.LayoutParams(0, -2, 1f))
            addView(right)
        }
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12); setMargins(dpi(6), dpi(12), dpi(6), 0) })

        // Apps
        col.addView(label("TUS APPS", 11f, C.MUTED, true).apply { letterSpacing = 0.12f; setPadding(dpi(2), dpi(26), 0, dpi(10)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        Prefs.APPS.chunked(2).forEachIndexed { r, pair ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            pair.forEachIndexed { i, app ->
                line.addView(softButton(text = app.name, oval = false).apply {
                    setOnClickListener { haptic(); TvRemote.openApp(app.link) }
                }, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { if (i > 0) marginStart = dpi(10) })
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dpi(10) })
        }
        col.addView(grid)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(C.BG)
            isFillViewport = true
            clipChildren = false
            addView(col)
        })

        TvRemote.listener = { s -> renderState(s) }
        renderState(TvRemote.state)
        TvRemote.start(this, host)
    }

    private fun renderState(s: TvRemote.State) {
        val (text, color) = when (s) {
            TvRemote.State.CONNECTED -> "CONECTADO" to C.GREEN
            TvRemote.State.CONNECTING -> "CONECTANDO…" to C.AMBER
            TvRemote.State.DISCONNECTED -> "SIN CONEXIÓN · TOCA PARA REINTENTAR" to C.RED
            TvRemote.State.NEEDS_PAIRING -> "HAY QUE VINCULAR DE NUEVO" to C.RED
        }
        statusText?.text = text
        (statusDot?.background as? GradientDrawable)?.setColor(color)
        statusText?.setOnClickListener {
            when (TvRemote.state) {
                TvRemote.State.DISCONNECTED -> Prefs.host(this)?.let { TvRemote.start(this, it) }
                TvRemote.State.NEEDS_PAIRING -> Prefs.host(this)?.let { startPairing(it, Prefs.name(this)) }
                else -> {}
            }
        }
        if (s == TvRemote.State.NEEDS_PAIRING && onRemote) {
            Prefs.host(this)?.let { startPairing(it, Prefs.name(this)) }
        }
    }

    private fun deviceMenu() {
        AlertDialog.Builder(this)
            .setTitle(Prefs.name(this))
            .setItems(arrayOf("Reconectar", "Volver a vincular", "Cambiar de Chromecast")) { _, which ->
                when (which) {
                    0 -> { TvRemote.stop(); Prefs.host(this)?.let { TvRemote.start(this, it) } }
                    1 -> Prefs.host(this)?.let { TvRemote.stop(); startPairing(it, Prefs.name(this)) }
                    2 -> { TvRemote.stop(); Prefs.forget(this); showSetup() }
                }
            }
            .show()
    }

    private fun keyboard() {
        val d = Dialog(this, R.style.Theme_Mando_Dialog)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(20), dpi(20), dpi(16))
            background = GradientDrawable().apply { setColor(Color.parseColor("#151518")); cornerRadius = dp(24f); setStroke(dpi(1), C.HAIR) }
        }
        card.addView(label("Escribir en la TV", 18f, C.TEXT, true))
        card.addView(label("Primero selecciona el buscador en la TV.", 13f, C.MUTED).apply { setPadding(0, dpi(4), 0, dpi(14)) })
        val input = field("Escribe aquí").apply { imeOptions = EditorInfo.IME_ACTION_SEND }
        card.addView(input, LinearLayout.LayoutParams(-1, dpi(52)))
        val send = {
            val t = input.text.toString()
            if (t.isNotEmpty()) { TvRemote.type(t); input.setText("") }
        }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        row.addView(softButton(R.drawable.ic_backspace, oval = false, iconDp = 20).apply {
            repeatWhileHeld { TvRemote.key(Keys.DEL) }
        }, LinearLayout.LayoutParams(dpi(56), dpi(48)))
        row.addView(softButton(text = "Enviar", oval = false).apply {
            setOnClickListener { haptic(); send() }
        }, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { marginStart = dpi(10) })
        row.addView(softButton(text = "Buscar", oval = false, gold = true).apply {
            setOnClickListener { haptic(); send(); TvRemote.key(Keys.ENTER) }
        }, LinearLayout.LayoutParams(0, dpi(48), 1f).apply { marginStart = dpi(10) })
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14) })
        card.addView(label("Cerrar", 14f, C.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, dpi(14), 0, 0); setOnClickListener { d.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))
        d.setContentView(card)
        d.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { y = dpi(24) }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        d.show()
        input.requestFocus()
    }
}
