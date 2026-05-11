package com.smartamenities.app.session

import android.content.Context
import android.content.SharedPreferences
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
    private const val PREFS_NAME = "nav_session_state"
    private const val KEY_SEGMENT = "active_segment"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var sessionSeed: String? = null
    @Volatile
    private var activeSegment: DemoSegment = DemoSegment.SEGMENT_1

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_SEGMENT, DemoSegment.SEGMENT_1.name)
        activeSegment = DemoSegment.entries.firstOrNull { it.name == saved } ?: DemoSegment.SEGMENT_1
    }

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
            prefs?.edit()?.putString(KEY_SEGMENT, activeSegment.name)?.apply()
            activeSegment
        }
    }

    fun currentSessionSeed(): String {
        return sessionSeed ?: synchronized(this) {
            sessionSeed ?: UUID.randomUUID().toString().also { sessionSeed = it }
        }
    }
}