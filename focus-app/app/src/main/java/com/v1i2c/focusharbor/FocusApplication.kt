package com.v1i2c.focusharbor

import android.app.Application
import com.v1i2c.focusharbor.data.FocusRepository

class FocusApplication : Application() {
    lateinit var repository: FocusRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(this)
    }
}
