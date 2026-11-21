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
    /** Apps conocidas con su enlace de apertura en Google TV. */
    val CATALOG = listOf(
        TvApp("YouTube", "https://www.youtube.com"),
        TvApp("Netflix", "https://www.netflix.com/title"),
        TvApp("Disney+", "https://www.disneyplus.com"),
        TvApp("Prime Video", "https://app.primevideo.com"),
        TvApp("Max", "https://play.max.com"),
        TvApp("Spotify", "spotify://"),
        TvApp("YouTube Music", "https://music.youtube.com"),
        TvApp("YouTube Kids", "https://www.youtubekids.com"),
        TvApp("Paramount+", "https://www.paramountplus.com"),
        TvApp("Apple TV", "https://tv.apple.com"),
        TvApp("Crunchyroll", "https://www.crunchyroll.com"),
        TvApp("ViX", "https://vix.com"),
        TvApp("Pluto TV", "https://pluto.tv"),
        TvApp("Twitch", "https://www.twitch.tv"),
        TvApp("Plex", "plex://")
    )
    const val SLOTS = 6
    private val DEFAULTS = listOf("YouTube", "Netflix", "Disney+", "Prime Video", "Max", "Spotify")

    fun app(ctx: Context, slot: Int): TvApp? {
        val name = p(ctx).getString("app$slot", DEFAULTS.getOrNull(slot)) ?: return null
        if (name.isEmpty()) return null
        CATALOG.firstOrNull { it.name == name }?.let { return it }
        // App personalizada guardada como "nombre|enlace"
        val parts = name.split("|", limit = 2)
        return if (parts.size == 2) TvApp(parts[0], parts[1]) else null
    }

    fun setApp(ctx: Context, slot: Int, app: TvApp?) {
        val v = when {
            app == null -> ""
            CATALOG.any { it.name == app.name && it.link == app.link } -> app.name
            else -> "${app.name}|${app.link}"
        }
        p(ctx).edit().putString("app$slot", v).apply()
    }

    private fun p(ctx: Context) = ctx.getSharedPreferences("mando", Context.MODE_PRIVATE)

    fun host(ctx: Context) = p(ctx).getString("host", null)
    fun name(ctx: Context) = p(ctx).getString("name", "Chromecast") ?: "Chromecast"
    fun save(ctx: Context, host: String, name: String) =
        p(ctx).edit().putString("host", host).putString("name", name).apply()
    fun forget(ctx: Context) = p(ctx).edit().remove("host").remove("name").apply()

    fun touchMode(ctx: Context) = p(ctx).getBoolean("touch", false)
    fun setTouchMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("touch", v).apply()
}
