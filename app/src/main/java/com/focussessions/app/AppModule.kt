package com.focussessions.app

import android.content.Context
import androidx.room.Room
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.calendar.*
import com.focussessions.app.connectivity.NetworkMonitor
import com.focussessions.app.data.local.database.AppDatabase
import com.focussessions.app.data.local.database.dao.*
import com.focussessions.app.data.local.datastore.UserPreferencesDataStore
import com.focussessions.app.data.repository.*
import com.focussessions.app.sync.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Firebase ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().also { db ->
        // Enable offline persistence with 100MB cache
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {
                setSizeBytes(100L * 1024 * 1024)  // 100 MB
            })
        }
    }

    // ── Database ──────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "focus_sessions_db")
            .fallbackToDestructiveMigration()
            .build()

    // ── DAOs ──────────────────────────────────────────────────────────────────

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao                   = db.taskDao()
    @Provides fun provideFocusSessionDao(db: AppDatabase): FocusSessionDao   = db.focusSessionDao()
    @Provides fun provideDailyProgressDao(db: AppDatabase): DailyProgressDao = db.dailyProgressDao()
    @Provides fun provideFocusHistoryDao(db: AppDatabase): FocusHistoryDao   = db.focusHistoryDao()
    @Provides fun provideCalendarDao(db: AppDatabase): CalendarDao           = db.calendarDao()
    @Provides fun provideAnalyticsDao(db: AppDatabase): AnalyticsDao         = db.analyticsDao()
    @Provides fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao         = db.syncQueueDao()

    // ── DataStore ─────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext ctx: Context) =
        UserPreferencesDataStore(ctx)

    // ── Repositories ──────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideTaskRepository(dao: TaskDao) = TaskRepository(dao)

    @Provides @Singleton
    fun provideFocusSessionRepository(
        sDao: FocusSessionDao, pDao: DailyProgressDao,
        hDao: FocusHistoryDao, cDao: CalendarDao
    ) = FocusSessionRepository(sDao, pDao, hDao, cDao)

    @Provides @Singleton
    fun provideUserPreferencesRepository(ds: UserPreferencesDataStore) =
        UserPreferencesRepository(ds)

    // ── Calendar / Analytics ──────────────────────────────────────────────────

    @Provides @Singleton
    fun provideCalendarManager(dao: CalendarDao) = CalendarManager(dao)

    @Provides @Singleton
    fun provideAnalyticsEngine(hDao: FocusHistoryDao, aDao: AnalyticsDao) =
        AnalyticsEngine(hDao, aDao)

    @Provides @Singleton
    fun provideProductivityCalculator(hDao: FocusHistoryDao, aDao: AnalyticsDao) =
        ProductivityCalculator(hDao, aDao)

    // ── Auth + Sync ───────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideAuthManager(
        @ApplicationContext ctx: Context,
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ) = AuthManager(ctx, auth, firestore)

    @Provides @Singleton
    fun provideFirestoreSyncService(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        historyDao: FocusHistoryDao,
        taskDao: TaskDao,
        calendarDao: CalendarDao
    ) = FirestoreSyncService(auth, firestore, historyDao, taskDao, calendarDao)

    @Provides @Singleton
    fun provideSyncManager(
        @ApplicationContext ctx: Context,
        authManager: AuthManager,
        firestoreSync: FirestoreSyncService,
        syncQueueDao: SyncQueueDao,
        historyDao: FocusHistoryDao,
        taskDao: TaskDao,
        networkMonitor: NetworkMonitor
    ) = SyncManager(ctx, authManager, firestoreSync, syncQueueDao, historyDao, taskDao, networkMonitor)

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext ctx: Context) = NetworkMonitor(ctx)
}
