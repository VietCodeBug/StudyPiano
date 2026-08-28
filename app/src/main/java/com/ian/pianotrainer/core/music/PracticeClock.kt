package com.ian.pianotrainer.core.music

import android.os.SystemClock

interface PracticeClock {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

class SystemPracticeClock : PracticeClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
