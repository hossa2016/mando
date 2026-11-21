package com.hossa.mando

import android.content.Context

/** Códigos de tecla de Android que entiende Google TV. */
object Keys {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOL_UP = 24
    const val VOL_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val PLAY_PAUSE = 85
    const val NEXT = 87
    const val PREV = 88
    const val MUTE = 164
    const val SETTINGS = 176

    fun forChar(c: Char): Int? = when (c) {
        in 'a'..'z' -> 29 + (c - 'a')
        in '0'..'9' -> 7 + (c - '0')
        ' ' -> 62
        '.' -> 56
        ',' -> 55
        '-' -> 69
        '@' -> 77
        '/' -> 76
        '\n' -> ENTER
        else -> null
    }
}

data class TvApp(val name: String, val link: String)

object Prefs {
    val APPS = listOf(
        TvApp("YouTube", "https://www.youtube.com"),
        TvApp("Netflix", "https://www.netflix.com/title"),
        TvApp("Disney+", "https://www.disneyplus.com"),
        TvApp("Spotify", "spotify://")
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("mando", Context.MODE_PRIVATE)

    fun host(ctx: Context) = p(ctx).getString("host", null)
    fun name(ctx: Context) = p(ctx).getString("name", "Chromecast") ?: "Chromecast"
    fun save(ctx: Context, host: String, name: String) =
        p(ctx).edit().putString("host", host).putString("name", name).apply()
    fun forget(ctx: Context) = p(ctx).edit().remove("host").remove("name").apply()

    fun touchMode(ctx: Context) = p(ctx).getBoolean("touch", false)
    fun setTouchMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("touch", v).apply()
}
