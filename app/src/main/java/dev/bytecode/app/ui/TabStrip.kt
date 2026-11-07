package dev.bytecode.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.bytecode.app.R
import dev.bytecode.app.documents.OpenDocument
import dev.bytecode.editor.LanguageRegistry

/** The row of open-file tabs above the editor. */
class TabStrip(private val recycler: RecyclerView, private val listener: Listener) {

    interface Listener {
        fun onTabSelected(id: String)
        fun onTabCloseRequested(id: String)
        fun onTabMenuRequested(id: String, anchor: View)
    }

    private data class TabItem(val id: String, val name: String, val languageId: String, val dirty: Boolean, val active: Boolean)

    private val adapter = TabAdapter()

    init {
        recycler.layoutManager = LinearLayoutManager(recycler.context, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = adapter
        recycler.itemAnimator = null
    }

    fun submit(documents: List<OpenDocument>, activeId: String?) {
        val items = documents.map { TabItem(it.id, it.name, it.languageId, it.dirty, it.id == activeId) }
        adapter.submitList(items) {
            val index = items.indexOfFirst { it.active }
            if (index >= 0) recycler.scrollToPosition(index)
        }
    }

    private inner class TabAdapter : ListAdapter<TabItem, TabHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
            return TabHolder(view)
        }

        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private inner class TabHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.tab_icon)
        private val title: TextView = view.findViewById(R.id.tab_title)
        private val dirty: View = view.findViewById(R.id.tab_dirty)
        private val close: ImageView = view.findViewById(R.id.tab_close)
        private val closeArea: View = view.findViewById(R.id.tab_close_area)
        private val indicator: View = view.findViewById(R.id.tab_indicator)

        fun bind(item: TabItem) {
            val context = itemView.context
            icon.setImageDrawable(FileIconDrawable(LanguageRegistry.byId(item.languageId)))
            title.text = item.name
            title.setTextColor(Palette.color(context, if (item.active) R.color.bc_text else R.color.bc_text_secondary))
            dirty.visibility = if (item.dirty) View.VISIBLE else View.GONE
            close.visibility = if (item.dirty) View.GONE else View.VISIBLE
            indicator.setBackgroundColor(if (item.active) Palette.accent else Color.TRANSPARENT)
            if (item.active) {
                itemView.setBackgroundColor(Palette.color(context, R.color.bc_background))
            } else {
                itemView.setBackgroundResource(R.drawable.bg_item_ripple)
            }
            itemView.setOnClickListener { listener.onTabSelected(item.id) }
            itemView.setOnLongClickListener {
                listener.onTabMenuRequested(item.id, itemView)
                true
            }
            closeArea.setOnClickListener { listener.onTabCloseRequested(item.id) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TabItem>() {
            override fun areItemsTheSame(oldItem: TabItem, newItem: TabItem): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TabItem, newItem: TabItem): Boolean = oldItem == newItem
        }
    }
}
