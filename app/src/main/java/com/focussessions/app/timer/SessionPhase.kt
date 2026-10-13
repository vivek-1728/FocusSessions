package com.focussessions.app.timer

/**
 * Represents every possible state of the continuous focus-cycle engine.
 */
enum class SessionPhase {
    /** App just opened, no cycle started. */
    IDLE,

    /** Active focus window — music OFF, timer counting down. */
    FOCUS,

    /** Rest window between focus sessions — music ON, timer counting down. */
    BREAK,

    /** All target focus time accumulated — cycle is finished. */
    COMPLETED
}
