package com.snishaper.android

import android.app.Application
import com.snishaper.android.core.SnishaperEngine

/**
 * Starts the gomobile engine once per process. The engine is CA-free: no
 * certificate manager is ever initialised (see docs/analysis/stage1-ca-trim-report.md).
 */
class SnishaperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SnishaperEngine.initialize(this)
    }
}
