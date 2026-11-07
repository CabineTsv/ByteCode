package dev.bytecode.app.ui

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.bytecode.app.R
import dev.bytecode.app.run.LineSeverity
import dev.bytecode.app.run.RunConsole
import dev.bytecode.app.run.SourceLocation

enum class ConsoleChannel { OUTPUT, PROBLEMS, SEARCH, LOG }

data class ConsoleLine(val text: String, val severity: LineSeverity, val location: SourceLocation?)

/**
 * The bottom panel: four channels (Output, Problems, Search, Log), a drag handle to change its height,
 * and clickable lines that jump to a source location.
 */
class ConsolePanel(private val root: View, private val listener: Listener) : RunConsole {

    interface Listener {
        fun onLocationClicked(location: SourceLocation)
        fun onConsoleLayoutChanged(expanded: Boolean, heightPx: Int)
    }

    private val context = root.context
    private val handle: View = root.findViewById(R.id.console_handle)
    private val list: RecyclerView = root.findViewById(R.id.console_list)
    private val toggleButton: ImageButton = root.findViewById(R.id.console_toggle)
    private val clearButton: ImageButton = root.findViewById(R.id.console_clear)
    private val tabViews: Map<ConsoleChannel, TextView> = mapOf(
        ConsoleChannel.OUTPUT to root.findViewById<TextView>(R.id.console_tab_output),
        ConsoleChannel.PROBLEMS to root.findViewById<TextView>(R.id.console_tab_problems),
        ConsoleChannel.SEARCH to root.findViewById<TextView>(R.id.console_tab_search),
        ConsoleChannel.LOG to root.findViewById<TextView>(R.id.console_tab_log)
    )
    private val lines: Map<ConsoleChannel, ArrayList<ConsoleLine>> =
        ConsoleChannel.entries.associateWith { ArrayList<ConsoleLine>() }
    private val adapter = LineAdapter()
    private var channel = ConsoleChannel.OUTPUT
    private var problemCount = 0

    var isExpanded: Boolean = true
        private set

    var heightPx: Int = context.dp(180)
        private set

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        for ((tabChannel, view) in tabViews) {
            view.setOnClickListener {
                select(tabChannel)
                if (!isExpanded) setExpanded(true)
            }
        }
        toggleButton.setOnClickListener { setExpanded(!isExpanded) }
        clearButton.setOnClickListener { clear(channel) }
        installDragHandle()
        select(ConsoleChannel.OUTPUT)
        applyLayout()
    }

    // ---- RunConsole ----------------------------------------------------------------------------------------

    override fun beginRun(header: String, clear: Boolean) {
        select(ConsoleChannel.OUTPUT)
        setExpanded(true)
        if (clear) clear(ConsoleChannel.OUTPUT)
        append(ConsoleChannel.OUTPUT, ConsoleLine(header, LineSeverity.INFO, null))
    }

    override fun print(severity: LineSeverity, text: String, location: SourceLocation?) {
        append(ConsoleChannel.OUTPUT, ConsoleLine(text, severity, location))
    }

    // ---- content ---------------------------------------------------------------------------------------------

    fun append(target: ConsoleChannel, line: ConsoleLine) {
        val store = lines.getValue(target)
        store.add(line)
        if (store.size > MAX_LINES) {
            val removed = REMOVE_BATCH
            store.subList(0, removed).clear()
            if (target == channel) adapter.notifyItemRangeRemoved(0, removed)
        }
        if (target == channel) {
            val atBottom = !list.canScrollVertically(1)
            adapter.notifyItemInserted(store.size - 1)
            if (atBottom) list.scrollToPosition(store.size - 1)
        }
    }

    /** Replaces the whole content of a channel (used by Problems and Search). */
    fun setLines(target: ConsoleChannel, newLines: List<ConsoleLine>) {
        val store = lines.getValue(target)
        store.clear()
        store.addAll(newLines)
        if (target == channel) adapter.notifyDataSetChanged()
    }

    fun clear(target: ConsoleChannel) {
        lines.getValue(target).clear()
        if (target == ConsoleChannel.PROBLEMS) setProblemCount(0)
        if (target == channel) adapter.notifyDataSetChanged()
    }

    fun setProblemCount(count: Int) {
        problemCount = count
        val tab = tabViews.getValue(ConsoleChannel.PROBLEMS)
        tab.text = if (count > 0) context.getString(R.string.console_problems_count, count) else context.getString(R.string.console_problems)
    }

    fun select(target: ConsoleChannel) {
        channel = target
        for ((tabChannel, view) in tabViews) {
            val selected = tabChannel == target
            view.isSelected = selected
            view.setTextColor(Palette.color(context, if (selected) R.color.bc_text else R.color.bc_text_secondary))
            view.background = if (selected) selectedBackground() else ContextCompatDrawable.ripple(context)
        }
        adapter.notifyDataSetChanged()
        if (lines.getValue(target).isNotEmpty()) list.scrollToPosition(lines.getValue(target).size - 1)
    }

    // ---- size and visibility --------------------------------------------------------------------------------------

    fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) return
        isExpanded = expanded
        applyLayout()
        listener.onConsoleLayoutChanged(isExpanded, heightPx)
    }

    fun setHeightPx(value: Int) {
        heightPx = clampHeight(value)
        applyLayout()
    }

    /** Applies stored values without notifying the listener. */
    fun restore(expanded: Boolean, height: Int) {
        isExpanded = expanded
        heightPx = clampHeight(height)
        applyLayout()
    }

    private fun clampHeight(value: Int): Int {
        val parentHeight = (root.parent as? View)?.height ?: 0
        val maximum = if (parentHeight > 0) (parentHeight * 0.7f).toInt() else context.dp(480)
        return value.coerceIn(context.dp(96), maxOf(maximum, context.dp(96)))
    }

    private fun applyLayout() {
        val params = root.layoutParams
        if (isExpanded) {
            list.visibility = View.VISIBLE
            params.height = heightPx
        } else {
            list.visibility = View.GONE
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        root.layoutParams = params
        toggleButton.setImageResource(if (isExpanded) R.drawable.ic_expand_more else R.drawable.ic_expand_less)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installDragHandle() {
        var startY = 0f
        var startHeight = 0
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = if (isExpanded) root.height else heightPx
                    if (!isExpanded) {
                        isExpanded = true
                        list.visibility = View.VISIBLE
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    heightPx = clampHeight(startHeight + (startY - event.rawY).toInt())
                    applyLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.performClick()
                    listener.onConsoleLayoutChanged(isExpanded, heightPx)
                    true
                }
                else -> false
            }
        }
    }

    private fun selectedBackground(): Drawable {
        val layers = LayerDrawable(arrayOf<Drawable>(ColorDrawable(Palette.accent)))
        layers.setLayerGravity(0, Gravity.BOTTOM)
        layers.setLayerHeight(0, context.dp(2))
        return layers
    }

    // ---- list adapter ----------------------------------------------------------------------------------------------

    private inner class LineAdapter : RecyclerView.Adapter<LineHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_console_line, parent, false)
            return LineHolder(view as TextView)
        }

        override fun getItemCount(): Int = lines.getValue(channel).size

        override fun onBindViewHolder(holder: LineHolder, position: Int) {
            val line = lines.getValue(channel)[position]
            holder.bind(line)
        }
    }

    private inner class LineHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(line: ConsoleLine) {
            textView.text = line.text
            textView.setTextColor(colorFor(line.severity))
            val location = line.location
            if (location != null) {
                textView.setBackgroundResource(R.drawable.bg_item_ripple)
                textView.setOnClickListener { listener.onLocationClicked(location) }
            } else {
                textView.background = null
                textView.setOnClickListener(null)
                textView.isClickable = false
            }
        }
    }

    private fun colorFor(severity: LineSeverity): Int = Palette.color(
        context,
        when (severity) {
            LineSeverity.NORMAL -> R.color.bc_text
            LineSeverity.INFO -> R.color.bc_text_secondary
            LineSeverity.WARNING -> R.color.bc_warning
            LineSeverity.ERROR -> R.color.bc_error
            LineSeverity.SUCCESS -> R.color.bc_success
        }
    )

    private companion object {
        const val MAX_LINES = 5000
        const val REMOVE_BATCH = 500
    }
}

/** Creates the borderless ripple used by tabs and rows. */
private object ContextCompatDrawable {
    fun ripple(context: android.content.Context): Drawable =
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_item_ripple)!!
}
