package com.focussessions.app.sync

/**
 * Last-modified-wins conflict resolution strategy.
 * Both local and remote documents carry an [updatedAt] epoch-millis field.
 */
object ConflictResolver {

    enum class Winner { LOCAL, REMOTE }

    data class ResolutionResult(
        val winner: Map<String, Any?>,
        val side: Winner
    )

    fun resolve(
        local: Map<String, Any?>,
        remote: Map<String, Any?>
    ): ResolutionResult {
        val localTs  = (local["updatedAt"]  as? Long) ?: 0L
        val remoteTs = (remote["updatedAt"] as? Long) ?: 0L
        return when {
            localTs > remoteTs -> ResolutionResult(local,  Winner.LOCAL)
            else               -> ResolutionResult(remote, Winner.REMOTE)
        }
    }

    fun merge(base: Map<String, Any?>, override: Map<String, Any?>): Map<String, Any?> =
        base.toMutableMap().apply { putAll(override) }
}
