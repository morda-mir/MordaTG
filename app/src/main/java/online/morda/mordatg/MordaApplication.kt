package online.morda.mordatg

import android.app.Application
import online.morda.mordatg.updates.DailyUpdateWorker
import online.morda.mordatg.updates.AppUpdateManager

class MordaApplication : Application() {
    lateinit var updates: AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        updates = AppUpdateManager(this)
        DailyUpdateWorker.schedule(this)
    }
}
