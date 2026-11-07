package dev.bytecode.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dev.bytecode.app.settings.SettingsRepository

class ByteCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Dark first: the default theme is dark unless the user chose light or "follow system".
        AppCompatDelegate.setDefaultNightMode(SettingsRepository(this).nightMode())
    }
}
