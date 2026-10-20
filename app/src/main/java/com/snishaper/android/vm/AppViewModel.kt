package com.snishaper.android.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snishaper.android.core.AppSettings
import com.snishaper.android.core.BuildInfo
import com.snishaper.android.core.EngineStatus
import com.snishaper.android.core.SnishaperEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Thin ViewModel wrapper around [SnishaperEngine] shared by the screens in this
 * stage. Screen-specific ViewModels (Stage 4) can compose/reuse the same engine
 * singleton.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = SnishaperEngine

    val status: StateFlow<EngineStatus> = engine.status
    val ready = engine.ready
    val settings = engine.settings
    val buildInfo = engine.buildInfo

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { error ->
                _messages.tryEmit(error.message ?: "操作失败")
            }
        }
    }

    fun startProxy() = launchAction { engine.startProxy() }

    fun stopProxy() = launchAction { engine.stopProxy() }

    fun refreshStatus() = launchAction { engine.refreshStatus() }

    fun setListenPort(port: Int) = launchAction { engine.setListenPort(port) }

    fun applySettings(patch: (org.json.JSONObject) -> Unit) =
        launchAction { engine.patchSettings(patch) }

    fun currentSettings(): AppSettings = engine.settings.value

    fun buildInfoOrNull(): BuildInfo = engine.buildInfo.value
}
