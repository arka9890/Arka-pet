package com.example.pet

import android.app.Application
import android.content.Context

class PetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

/** Process-wide dependencies; filled in by later tasks. */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
