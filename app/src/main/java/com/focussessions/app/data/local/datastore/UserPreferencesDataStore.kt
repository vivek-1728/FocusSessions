package com.focussessions.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.focussessions.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Keys ──────────────────────────────────────────────────────────────────
    private object Keys {
        val FOCUS_DURATION       = intPreferencesKey("focus_duration_minutes")
        val SHORT_BREAK          = intPreferencesKey("short_break_minutes")
        val LONG_BREAK           = intPreferencesKey("long_break_minutes")
        val SESSIONS_BEFORE_LONG = intPreferencesKey("sessions_before_long_break")
        val DAILY_GOAL           = intPreferencesKey("daily_goal_minutes")
        val AUTO_START_BREAKS    = booleanPreferencesKey("auto_start_breaks")
        val AUTO_START_FOCUS     = booleanPreferencesKey("auto_start_focus")
        val MUSIC_ENABLED        = booleanPreferencesKey("music_enabled")
        val SELECTED_MUSIC_URI   = stringPreferencesKey("selected_music_uri")
        val NOTIFICATIONS        = booleanPreferencesKey("notifications_enabled")
        val STREAK_COUNT         = intPreferencesKey("streak_count")
        val LAST_ACTIVE_DATE     = stringPreferencesKey("last_active_date")
    }

    // ── Read ─────────────────────────────────────────────────────────────────
    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserPreferences(
                focusDurationMinutes      = prefs[Keys.FOCUS_DURATION]       ?: 25,
                shortBreakMinutes         = prefs[Keys.SHORT_BREAK]          ?: 5,
                longBreakMinutes          = prefs[Keys.LONG_BREAK]           ?: 15,
                sessionsBeforeLongBreak   = prefs[Keys.SESSIONS_BEFORE_LONG] ?: 4,
                dailyGoalMinutes          = prefs[Keys.DAILY_GOAL]           ?: 120,
                autoStartBreaks           = prefs[Keys.AUTO_START_BREAKS]    ?: false,
                autoStartFocus            = prefs[Keys.AUTO_START_FOCUS]     ?: false,
                musicEnabled              = prefs[Keys.MUSIC_ENABLED]        ?: false,
                selectedMusicUri          = prefs[Keys.SELECTED_MUSIC_URI]   ?: "",
                notificationsEnabled      = prefs[Keys.NOTIFICATIONS]        ?: true,
                streakCount               = prefs[Keys.STREAK_COUNT]         ?: 0,
                lastActiveDate            = prefs[Keys.LAST_ACTIVE_DATE]     ?: ""
            )
        }

    // ── Write ─────────────────────────────────────────────────────────────────
    suspend fun setFocusDuration(minutes: Int) {
        context.dataStore.edit { it[Keys.FOCUS_DURATION] = minutes }
    }

    suspend fun setShortBreak(minutes: Int) {
        context.dataStore.edit { it[Keys.SHORT_BREAK] = minutes }
    }

    suspend fun setLongBreak(minutes: Int) {
        context.dataStore.edit { it[Keys.LONG_BREAK] = minutes }
    }

    suspend fun setSessionsBeforeLongBreak(count: Int) {
        context.dataStore.edit { it[Keys.SESSIONS_BEFORE_LONG] = count }
    }

    suspend fun setDailyGoal(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL] = minutes }
    }

    suspend fun setAutoStartBreaks(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_BREAKS] = enabled }
    }

    suspend fun setAutoStartFocus(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_FOCUS] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }
    }

    suspend fun setSelectedMusicUri(uri: String) {
        context.dataStore.edit { it[Keys.SELECTED_MUSIC_URI] = uri }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setStreakCount(count: Int) {
        context.dataStore.edit { it[Keys.STREAK_COUNT] = count }
    }

    suspend fun setLastActiveDate(date: String) {
        context.dataStore.edit { it[Keys.LAST_ACTIVE_DATE] = date }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun updateAllPreferences(prefs: UserPreferences) {
        context.dataStore.edit {
            it[Keys.FOCUS_DURATION]       = prefs.focusDurationMinutes
            it[Keys.SHORT_BREAK]          = prefs.shortBreakMinutes
            it[Keys.LONG_BREAK]           = prefs.longBreakMinutes
            it[Keys.SESSIONS_BEFORE_LONG] = prefs.sessionsBeforeLongBreak
            it[Keys.DAILY_GOAL]           = prefs.dailyGoalMinutes
            it[Keys.AUTO_START_BREAKS]    = prefs.autoStartBreaks
            it[Keys.AUTO_START_FOCUS]     = prefs.autoStartFocus
            it[Keys.MUSIC_ENABLED]        = prefs.musicEnabled
            it[Keys.SELECTED_MUSIC_URI]   = prefs.selectedMusicUri
            it[Keys.NOTIFICATIONS]        = prefs.notificationsEnabled
            it[Keys.STREAK_COUNT]         = prefs.streakCount
            it[Keys.LAST_ACTIVE_DATE]     = prefs.lastActiveDate
        }
    }
}
