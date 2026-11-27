package com.sumipods.app

import android.app.Application
import com.sumipods.app.ble.AirPodsRepository

class SumiPodsApplication : Application() {
    val repository: AirPodsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AirPodsRepository(this)
    }
}
