package dev.personalhub.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("settings")

// Application composition owns repositories; feature modules consume public APIs.
class PersonalHubApplication : Application() {
    val settings by lazy { SettingsStore(this) }
}
class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.preferences.data.map { it[themeKey] ?: "system" }
    suspend fun theme(value: String) {
        require(value in setOf("system", "dark", "light"))
        context.preferences.edit { it[themeKey] = value }
    }
}
