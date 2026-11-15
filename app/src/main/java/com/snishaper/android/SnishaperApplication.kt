package com.snishaper.android

import android.app.Application
import com.snishaper.android.core.SnishaperEngine

/**
 * Starts the gomobile engine once per process. The engine carries the full
 * desktop feature surface, including the certificate manager (MITM/QUIC CA
 * material, published into the Android system store via root su).
 */
class SnishaperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SnishaperEngine.initialize(this)
    }
}
