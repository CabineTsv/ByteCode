package dev.bytecode.app.ui

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

/** Colours that are chosen at runtime. Everything else comes from resources (res/values[-night]/colors.xml). */
object Palette {
    /** The user's accent colour; set by MainActivity from the settings. */
    @JvmStatic
    var accent: Int = 0xFF4EA1FF.toInt()

    fun color(context: Context, @ColorRes id: Int): Int = ContextCompat.getColor(context, id)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
