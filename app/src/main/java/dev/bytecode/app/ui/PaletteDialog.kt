package dev.bytecode.app.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.bytecode.app.R

class PaletteItem(val title: String, val detail: String?, val action: () -> Unit)

/**
 * A searchable list in a dialog: the command palette, "go to file" and "recent files".
 * Typing filters the list; every word must appear in the title or the detail.
 */
class PaletteDialog(private val context: Context, private val title: String) {

    private var allItems: List<PaletteItem> = emptyList()
    private var shown: List<PaletteItem> = emptyList()
    private var dialog: AlertDialog? = null
    private var input: EditText? = null
    private val adapter = ItemAdapter()

    fun show(initialItems: List<PaletteItem>) {
        allItems = initialItems
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_palette, null)
        val field: EditText = view.findViewById(R.id.palette_input)
        val list: RecyclerView = view.findViewById(R.id.palette_list)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refilter(s?.toString().orEmpty())
            }
        })
        input = field

        val created = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        created.setOnShowListener {
            field.requestFocus()
            created.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        created.setOnDismissListener {
            dialog = null
            input = null
        }
        dialog = created
        refilter("")
        created.show()
        Dialogs.tint(created)
    }

    /** Replaces the items (for example when an index finishes loading) and keeps the typed filter. */
    fun setItems(items: List<PaletteItem>) {
        allItems = items
        refilter(input?.text?.toString().orEmpty())
    }

    private fun refilter(query: String) {
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val matches = if (words.isEmpty()) {
            allItems
        } else {
            allItems.filter { item ->
                val haystack = (item.title + " " + (item.detail ?: "")).lowercase()
                words.all { haystack.contains(it) }
            }
        }
        shown = matches.take(MAX_SHOWN)
        adapter.notifyDataSetChanged()
    }

    private inner class ItemAdapter : RecyclerView.Adapter<ItemHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_palette_row, parent, false)
            return ItemHolder(view)
        }

        override fun getItemCount(): Int = shown.size

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            holder.bind(shown[position])
        }
    }

    private inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.palette_title)
        private val detailView: TextView = view.findViewById(R.id.palette_detail)

        fun bind(item: PaletteItem) {
            titleView.text = item.title
            detailView.text = item.detail.orEmpty()
            detailView.visibility = if (item.detail.isNullOrEmpty()) View.GONE else View.VISIBLE
            itemView.setOnClickListener {
                dialog?.dismiss()
                item.action()
            }
        }
    }

    private companion object {
        const val MAX_SHOWN = 200
    }
}
