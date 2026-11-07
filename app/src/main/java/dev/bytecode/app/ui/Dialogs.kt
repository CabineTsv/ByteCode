package dev.bytecode.app.ui

import android.content.Context
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.bytecode.app.R

/** Small dialog helpers so the rest of the UI code stays short. */
object Dialogs {

    /** Shows the dialog with the user's accent colour on its buttons. */
    fun show(builder: MaterialAlertDialogBuilder): AlertDialog {
        val dialog = builder.create()
        dialog.show()
        tint(dialog)
        return dialog
    }

    fun tint(dialog: AlertDialog) {
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.setTextColor(Palette.accent)
        }
    }

    /** Asks for one line of text. [onResult] receives the trimmed text. */
    fun promptText(
        context: Context,
        @StringRes title: Int,
        @StringRes hint: Int,
        initial: String,
        @StringRes positive: Int,
        onResult: (String) -> Unit
    ) {
        val layout = TextInputLayout(context)
        layout.hint = context.getString(hint)
        val edit = TextInputEditText(layout.context)
        edit.setText(initial)
        edit.setSingleLine()
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        layout.addView(edit)
        val container = FrameLayout(context)
        container.setPadding(context.dp(24), context.dp(8), context.dp(24), 0)
        container.addView(layout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positive) { _, _ -> onResult(edit.text?.toString().orEmpty().trim()) }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            edit.requestFocus()
            edit.setSelection(0, initial.substringBeforeLast('.', initial).length)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
                true
            } else {
                false
            }
        }
        dialog.show()
        tint(dialog)
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        @StringRes positive: Int,
        onConfirm: () -> Unit
    ) {
        show(
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive) { _, _ -> onConfirm() }
                .setNegativeButton(R.string.action_cancel, null)
        )
    }

    fun choose(context: Context, title: String, items: List<String>, onChoose: (Int) -> Unit) {
        show(
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(items.toTypedArray()) { _, which -> onChoose(which) }
                .setNegativeButton(R.string.action_cancel, null)
        )
    }
}
