package com.smartamenities.app.session

import java.util.UUID

enum class DemoSegment(val apiValue: String) {
    SEGMENT_1("SEGMENT_1"),
    SEGMENT_2("SEGMENT_2"),
    SEGMENT_3("SEGMENT_3"),
    SEGMENT_4("SEGMENT_4");

    fun next(): DemoSegment {
        return when (this) {
            SEGMENT_1 -> SEGMENT_2
            SEGMENT_2 -> SEGMENT_3
            SEGMENT_3 -> SEGMENT_4
            SEGMENT_4 -> SEGMENT_1
        }
    }
}

object NavigationSessionState {
    @Volatile
    private var sessionSeed: String? = null
    @Volatile
    private var activeSegment: DemoSegment = DemoSegment.SEGMENT_1

    fun startNewSession(): String {
        val newSeed = UUID.randomUUID().toString()
        sessionSeed = newSeed
        return newSeed
    }

    fun currentSegment(): DemoSegment {
        return activeSegment
    }

    fun isDeviationRerouteAllowed(): Boolean {
        return currentSegment() == DemoSegment.SEGMENT_4
    }

    fun advanceToNextSegment(): DemoSegment {
        return synchronized(this) {
            activeSegment = activeSegment.next()
            activeSegment
        }
    }

    fun currentSessionSeed(): String {
        return sessionSeed ?: synchronized(this) {
            sessionSeed ?: UUID.randomUUID().toString().also { sessionSeed = it }
        }
    }
}