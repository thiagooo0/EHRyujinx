package org.kenjinx.android

import android.app.Application
import android.content.Context
import java.io.File
import org.kenjinx.android.controllers.ControllerMatchingManager

class KenjinxApplication : Application() {
    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        ControllerMatchingManager.initialize(applicationContext)
    }

    fun getPublicFilesDir(): File = getExternalFilesDir(null) ?: filesDir

    companion object {
        lateinit var instance: KenjinxApplication
            private set

        val context: Context get() = instance.applicationContext
    }
}
