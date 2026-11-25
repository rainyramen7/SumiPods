package com.sumipods.app

import android.app.Application
import com.sumipods.app.ble.AirPodsRepository

class SumiPodsApplication : Application() {
    lateinit var repository: AirPodsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AirPodsRepository(this)
    }
}
