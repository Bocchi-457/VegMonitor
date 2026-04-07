package com.boc.vegmonitor

import android.app.Application
import com.boc.vegmonitor.data.AppDatabase

class VegMonitorApplication : Application() {
    // 使用 lazy 委托，只有在第一次使用时才初始化数据库
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}