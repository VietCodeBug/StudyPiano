package com.ian.pianotrainer.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PianoTrainerApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Clean legacy demo artifacts if any
        applicationScope.launch {
            container.databaseMaintenance.cleanLegacyDemoDataIfNeeded()
        }
    }
}
