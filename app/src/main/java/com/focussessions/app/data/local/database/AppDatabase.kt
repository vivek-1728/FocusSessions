package com.focussessions.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.focussessions.app.data.local.database.dao.*
import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.sync.SyncQueueDao
import com.focussessions.app.sync.SyncQueueEntity

@Database(
    entities = [
        TaskEntity::class,
        FocusSessionEntity::class,
        DailyProgressEntity::class,
        FocusHistoryEntity::class,
        CalendarDayEntity::class,
        ProductivityInsightEntity::class,
        SyncQueueEntity::class
    ],
    version      = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun focusHistoryDao(): FocusHistoryDao
    abstract fun calendarDao(): CalendarDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun syncQueueDao(): SyncQueueDao
}
