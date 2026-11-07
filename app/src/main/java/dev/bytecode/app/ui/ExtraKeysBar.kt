package dev.bytecode.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.bytecode.app.R

/**
 * A scrolling row of keys that phone keyboards lack: Tab, undo/redo, arrows and common code symbols.
 * The keys never take focus, so the soft keyboard stays open while they are used.
 */
class ExtraKeysBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    interface Listener {
        fun onInsertText(text: String)
        fun onEditorAction(name: String)
    }

    var listener: Listener? = null

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        isFocusableInTouchMode = false

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))

        addActionKey(row, R.drawable.ic_tab_key, R.string.key_tab, "insertTab", false)
        addActionKey(row, R.drawable.ic_undo, R.string.action_undo, "undo", false)
        addActionKey(row, R.drawable.ic_redo, R.string.action_redo, "redo", false)
        addActionKey(row, R.drawable.ic_arrow_left, R.string.key_left, "cursorLeft", true)
        addActionKey(row, R.drawable.ic_arrow_down, R.string.key_down, "cursorDown", true)
        addActionKey(row, R.drawable.ic_arrow_up, R.string.key_up, "cursorUp", true)
        addActionKey(row, R.drawable.ic_arrow_right, R.string.key_right, "cursorRight", true)
        for (symbol in SYMBOLS) addSymbolKey(row, symbol)

        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    private fun newKeyContainerParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(context.dp(40), LayoutParams.MATCH_PARENT)
        params.setMargins(context.dp(2), 0, context.dp(2), 0)
        return params
    }

    private fun addActionKey(row: LinearLayout, @DrawableRes icon: Int, @StringRes description: Int, action: String, repeats: Boolean) {
        val key = ImageView(context)
        key.setImageResource(icon)
        key.scaleType = ImageView.ScaleType.CENTER
        key.setBackgroundResource(R.drawable.bg_key)
        key.contentDescription = context.getString(description)
        key.isFocusable = false
        key.isFocusableInTouchMode = false
        if (repeats) {
            key.setOnTouchListener(RepeatTouchListener { listener?.onEditorAction(action) })
        } else {
            key.setOnClickListener { listener?.onEditorAction(action) }
        }
        row.addView(key, newKeyContainerParams())
    }

    private fun addSymbolKey(row: LinearLayout, symbol: String) {
        val key = TextView(context)
        key.text = symbol
        key.gravity = Gravity.CENTER
        key.typeface = android.graphics.Typeface.MONOSPACE
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        key.setTextColor(Palette.color(context, R.color.bc_text))
        key.setBackgroundResource(R.drawable.bg_key)
        key.contentDescription = symbol
        key.isFocusable = false
        key.isFocusableInTouchMode = false
        key.setOnClickListener { listener?.onInsertText(symbol) }
        row.addView(key, newKeyContainerParams())
    }

    /** Runs the action on press and keeps repeating it while the key is held. */
    private class RepeatTouchListener(private val action: () -> Unit) : View.OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private val repeat = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, REPEAT_INTERVAL_MILLIS)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    action()
                    handler.postDelayed(repeat, REPEAT_DELAY_MILLIS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            return true
        }

        private companion object {
            const val REPEAT_DELAY_MILLIS = 350L
            const val REPEAT_INTERVAL_MILLIS = 70L
        }
    }

    private companion object {
        val SYMBOLS = listOf(
            "{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "=", "+", "-", "*", "/", "\\",
            "|", "&", "!", "?", "#", "_", "$", "%", "@", ".", ",", "~", "`", "^"
        )
    }
}
