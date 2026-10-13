package com.focussessions.app.auth

/**
 * In-memory representation of the signed-in user.
 * Stored in DataStore (uid only) and Firestore (full profile).
 */
data class UserProfile(
    val uid: String          = "",
    val displayName: String  = "",
    val email: String        = "",
    val photoUrl: String     = "",
    val lastSyncTime: Long   = 0L,
    val totalFocusMinutes: Int = 0,
    val currentStreak: Int   = 0,
    val createdAt: Long      = System.currentTimeMillis()
) {
    val isLoggedIn: Boolean get() = uid.isNotEmpty()

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "uid"               to uid,
        "displayName"       to displayName,
        "email"             to email,
        "photoUrl"          to photoUrl,
        "lastSyncTime"      to lastSyncTime,
        "totalFocusMinutes" to totalFocusMinutes,
        "currentStreak"     to currentStreak,
        "createdAt"         to createdAt,
        "updatedAt"         to System.currentTimeMillis()
    )

    companion object {
        fun fromFirestoreMap(m: Map<String, Any>): UserProfile = UserProfile(
            uid               = m["uid"] as? String ?: "",
            displayName       = m["displayName"] as? String ?: "",
            email             = m["email"] as? String ?: "",
            photoUrl          = m["photoUrl"] as? String ?: "",
            lastSyncTime      = (m["lastSyncTime"] as? Number)?.toLong() ?: 0L,
            totalFocusMinutes = (m["totalFocusMinutes"] as? Number)?.toInt() ?: 0,
            currentStreak     = (m["currentStreak"] as? Number)?.toInt() ?: 0,
            createdAt         = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

/** Sync status exposed to the UI. */
enum class SyncStatus { IDLE, SYNCING, SYNCED, ERROR, OFFLINE }

data class SyncState(
    val status: SyncStatus   = SyncStatus.IDLE,
    val lastSyncTime: Long   = 0L,
    val pendingOps: Int      = 0,
    val errorMessage: String = ""
)
