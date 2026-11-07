package dev.bytecode.app

import androidx.annotation.StringRes

/** Everything the user can trigger from the command palette, the toolbar menu or a keyboard shortcut. */
enum class AppCommand(@StringRes val title: Int, val shortcut: String? = null) {
    SAVE(R.string.action_save, "Ctrl+S"),
    SAVE_ALL(R.string.action_save_all, "Ctrl+Shift+S"),
    RUN(R.string.action_run, "F5"),
    STOP(R.string.action_stop),
    FIND(R.string.action_find, "Ctrl+F"),
    REPLACE(R.string.action_replace, "Ctrl+H"),
    GOTO_LINE(R.string.action_goto_line, "Ctrl+Alt+G"),
    TOGGLE_COMMENT(R.string.action_toggle_comment, "Ctrl+/"),
    FOLD_ALL(R.string.action_fold_all),
    UNFOLD_ALL(R.string.action_unfold_all),
    UNDO(R.string.action_undo, "Ctrl+Z"),
    REDO(R.string.action_redo, "Ctrl+Shift+Z"),
    QUICK_OPEN(R.string.action_quick_open, "Ctrl+P"),
    COMMAND_PALETTE(R.string.action_command_palette, "Ctrl+Shift+P"),
    SEARCH_FILES(R.string.action_search_files, "Ctrl+Shift+F"),
    RECENT_FILES(R.string.action_recent_files),
    RECENT_WORKSPACES(R.string.action_recent_workspaces),
    NEW_FILE(R.string.action_new_file),
    NEW_FOLDER(R.string.action_new_folder),
    OPEN_FOLDER(R.string.action_open_folder),
    OPEN_FILE(R.string.action_open_file),
    CLOSE_TAB(R.string.action_close_tab, "Ctrl+W"),
    CLOSE_ALL_TABS(R.string.action_close_all_tabs),
    NEXT_TAB(R.string.action_next_tab, "Ctrl+Tab"),
    PREVIOUS_TAB(R.string.action_previous_tab, "Ctrl+Shift+Tab"),
    TOGGLE_EXPLORER(R.string.action_toggle_explorer, "Ctrl+B"),
    TOGGLE_CONSOLE(R.string.action_toggle_console, "Ctrl+J"),
    CLEAR_CONSOLE(R.string.action_clear_output),
    REFRESH_EXPLORER(R.string.action_refresh),
    TOGGLE_WORD_WRAP(R.string.action_toggle_word_wrap),
    INCREASE_FONT(R.string.action_increase_font),
    DECREASE_FONT(R.string.action_decrease_font),
    SETTINGS(R.string.action_settings),
    ABOUT(R.string.action_about)
}
