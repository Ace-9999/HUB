package dev.personalhub.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.personalhub.college.data.RoomCollegeRepository
import dev.personalhub.core.AppTime
import dev.personalhub.feature.college.SessionHooks
import dev.personalhub.focus.data.RoomFocusRepository
import dev.personalhub.vtop.VtopSyncView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import java.time.ZoneId

private val Context.preferences by preferencesDataStore("settings")

// Application composition owns repositories; feature modules consume public APIs only.
class PersonalHubApplication : Application() {
    val settings by lazy { SettingsStore(this) }
    val time by lazy { AppTime(zone = ZoneId.systemDefault()) }
    val focus by lazy { RoomFocusRepository.open(this, time) }
    val college by lazy { RoomCollegeRepository.open(this) }
    /** Outlives activities so an acknowledged sync save is not cancelled by closing the portal screen. */
    val scope = CoroutineScope(SupervisorJob())
    override fun onCreate() {
        super.onCreate()
        SessionHooks.clear = { VtopSyncView.clearSession() }
    }
}
class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.preferences.data.map { it[themeKey] ?: DEFAULT_THEME }
    suspend fun theme(value: String) {
        require(value in setOf("system", "dark", "light"))
        context.preferences.edit { it[themeKey] = value }
    }
    companion object { const val DEFAULT_THEME = "light" }
}
