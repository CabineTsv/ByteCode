package dev.bytecode.app.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class RecentWorkspace(val uri: String, val name: String)
data class RecentFile(val uri: String, val name: String)

/** Persists workspaces, recent files, the open-tab session and a few layout values. */
class WorkspaceStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("bytecode_state", Context.MODE_PRIVATE)

    var lastWorkspaceUri: String?
        get() = prefs.getString(KEY_LAST_WORKSPACE, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_WORKSPACE, value).apply()
        }

    var explorerWidthDp: Int
        get() = prefs.getInt(KEY_EXPLORER_WIDTH, 280)
        set(value) {
            prefs.edit().putInt(KEY_EXPLORER_WIDTH, value).apply()
        }

    var explorerDockVisible: Boolean
        get() = prefs.getBoolean(KEY_EXPLORER_DOCK_VISIBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EXPLORER_DOCK_VISIBLE, value).apply()
        }

    var consoleHeightDp: Int
        get() = prefs.getInt(KEY_CONSOLE_HEIGHT, 180)
        set(value) {
            prefs.edit().putInt(KEY_CONSOLE_HEIGHT, value).apply()
        }

    var consoleVisible: Boolean
        get() = prefs.getBoolean(KEY_CONSOLE_VISIBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CONSOLE_VISIBLE, value).apply()
        }

    // ---- permissions ------------------------------------------------------------------------------------

    /** Keeps access to [uri] after the app restarts. */
    fun takePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (error: SecurityException) {
            // Some providers only grant read access or do not support persistable grants.
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: SecurityException) {
                // The grant is only valid for this process; the user will be asked again next time.
            }
        }
    }

    fun hasPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    // ---- recent workspaces and files ---------------------------------------------------------------------

    fun recentWorkspaces(): List<RecentWorkspace> = readArray(KEY_RECENT_WORKSPACES) { item ->
        RecentWorkspace(item.getString("uri"), item.getString("name"))
    }

    fun addRecentWorkspace(uri: Uri, name: String) {
        val updated = ArrayList<RecentWorkspace>()
        updated.add(RecentWorkspace(uri.toString(), name))
        updated.addAll(recentWorkspaces().filter { it.uri != uri.toString() })
        writeArray(KEY_RECENT_WORKSPACES, updated.take(MAX_RECENT_WORKSPACES)) { JSONObject().put("uri", it.uri).put("name", it.name) }
    }

    fun recentFiles(): List<RecentFile> = readArray(KEY_RECENT_FILES) { item ->
        RecentFile(item.getString("uri"), item.getString("name"))
    }

    fun addRecentFile(uri: Uri, name: String) {
        val updated = ArrayList<RecentFile>()
        updated.add(RecentFile(uri.toString(), name))
        updated.addAll(recentFiles().filter { it.uri != uri.toString() })
        writeArray(KEY_RECENT_FILES, updated.take(MAX_RECENT_FILES)) { JSONObject().put("uri", it.uri).put("name", it.name) }
    }

    fun removeRecentFile(uri: String) {
        val updated = recentFiles().filter { it.uri != uri }
        writeArray(KEY_RECENT_FILES, updated) { JSONObject().put("uri", it.uri).put("name", it.name) }
    }

    // ---- explorer state --------------------------------------------------------------------------------------

    fun expandedFolders(workspaceUri: String): Set<String> =
        prefs.getStringSet(KEY_EXPANDED_PREFIX + workspaceUri.hashCode(), emptySet())?.toSet() ?: emptySet()

    fun setExpandedFolders(workspaceUri: String, ids: Set<String>) {
        prefs.edit().putStringSet(KEY_EXPANDED_PREFIX + workspaceUri.hashCode(), HashSet(ids)).apply()
    }

    // ---- session (open tabs) ---------------------------------------------------------------------------------

    fun saveSession(openUris: List<String>, activeUri: String?) {
        val array = JSONArray()
        openUris.forEach { array.put(it) }
        prefs.edit().putString(KEY_SESSION_TABS, array.toString()).putString(KEY_SESSION_ACTIVE, activeUri).apply()
    }

    fun sessionTabs(): List<String> {
        val text = prefs.getString(KEY_SESSION_TABS, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getString(it) }
        } catch (error: JSONException) {
            emptyList()
        }
    }

    fun sessionActive(): String? = prefs.getString(KEY_SESSION_ACTIVE, null)

    // ---- helpers --------------------------------------------------------------------------------------------------

    private fun <T> readArray(key: String, parse: (JSONObject) -> T): List<T> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { parse(array.getJSONObject(it)) }
        } catch (error: JSONException) {
            emptyList()
        }
    }

    private fun <T> writeArray(key: String, items: List<T>, encode: (T) -> JSONObject) {
        val array = JSONArray()
        items.forEach { array.put(encode(it)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val KEY_LAST_WORKSPACE = "last_workspace"
        const val KEY_EXPLORER_WIDTH = "explorer_width_dp"
        const val KEY_EXPLORER_DOCK_VISIBLE = "explorer_dock_visible"
        const val KEY_CONSOLE_HEIGHT = "console_height_dp"
        const val KEY_CONSOLE_VISIBLE = "console_visible"
        const val KEY_RECENT_WORKSPACES = "recent_workspaces"
        const val KEY_RECENT_FILES = "recent_files"
        const val KEY_EXPANDED_PREFIX = "expanded_"
        const val KEY_SESSION_TABS = "session_tabs"
        const val KEY_SESSION_ACTIVE = "session_active"
        const val MAX_RECENT_WORKSPACES = 8
        const val MAX_RECENT_FILES = 25
    }
}
