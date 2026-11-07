package dev.bytecode.app.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import dev.bytecode.app.R
import dev.bytecode.luau.LuauRuntime
import java.io.File
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(SettingsRepository(this).accentOverlay(), true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFont(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(SettingsRepository.KEY_IMPORT_FONT)?.setOnPreferenceClickListener {
            fontPicker.launch(arrayOf("*/*"))
            true
        }

        findPreference<EditTextPreference>(SettingsRepository.KEY_ACCENT_CUSTOM)?.setOnPreferenceChangeListener { _, newValue ->
            val valid = AccentPresets.isValidHex(newValue as String)
            if (!valid) {
                Toast.makeText(requireContext(), R.string.settings_invalid_hex, Toast.LENGTH_SHORT).show()
            }
            valid
        }

        findPreference<ListPreference>(SettingsRepository.KEY_THEME)?.setOnPreferenceChangeListener { _, newValue ->
            val mode = when (newValue as String) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            true
        }

        findPreference<Preference>("about_version")?.summary = aboutSummary()
    }

    private fun aboutSummary(): String {
        val context = requireContext()
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (error: Exception) {
            "?"
        }
        val luau = LuauRuntime()
        val luauText = if (luau.isAvailable) "Luau ${luau.version}" else getString(R.string.about_luau_unavailable)
        return "ByteCode $appVersion · $luauText"
    }

    private fun importFont(uri: Uri) {
        val context = requireContext()
        val settings = SettingsRepository(context)
        val target = settings.customFontFile()
        val temporary = File(target.parentFile, "import.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FONT_BYTES) throw IOException("font too large")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("cannot open font")
            if (!looksLikeFont(temporary)) throw IOException("not a font")
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) throw IOException("cannot store font")
            findPreference<ListPreference>(SettingsRepository.KEY_FONT_FAMILY)?.value = "custom"
            Toast.makeText(context, R.string.settings_font_imported, Toast.LENGTH_SHORT).show()
        } catch (error: IOException) {
            temporary.delete()
            Toast.makeText(context, R.string.settings_font_failed, Toast.LENGTH_LONG).show()
        }
    }

    // TrueType (00 01 00 00, "true"), OpenType/CFF ("OTTO") or a collection ("ttcf").
    private fun looksLikeFont(file: File): Boolean {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return false
        val tag = String(header, Charsets.ISO_8859_1)
        return tag == "OTTO" || tag == "true" || tag == "ttcf" ||
            (header[0].toInt() == 0 && header[1].toInt() == 1 && header[2].toInt() == 0 && header[3].toInt() == 0)
    }

    private companion object {
        const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    }
}
