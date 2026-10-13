package com.focussessions.app.testutils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focussessions.app.data.local.database.AppDatabase

/**
 * Factory for creating in-memory Room database instances for unit tests.
 */
object TestDatabaseFactory {

    fun createInMemoryDatabase(
        context: Context = ApplicationProvider.getApplicationContext()
    ): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
