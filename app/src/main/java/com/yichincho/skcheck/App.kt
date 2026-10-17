package com.yichincho.skcheck

import android.app.Application
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.work.Notifier
import com.yichincho.skcheck.work.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannel(this)
        Scheduler.apply(this)
    }
}
