package com.snishaper.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.snishaper.android.MainActivity
import com.snishaper.android.R
import com.snishaper.android.core.SnishaperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single VpnService of the app. Establishes the TUN interface itself
 * (`Builder.establish()` → fd) and hands the raw fd to the Go engine via
 * `SnishaperEngine.startTun(fd, mtu)`; the sing-tun handler inside the core reads packets
 * from that fd (with_gvisor). Mirrors the desktop TUN mode minus the CA surface.
 */
class TunVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    /**
     * The engine bridge is suspend-only (`SnishaperEngine.startTun` / `stopTun` hop to the
     * Go side on Dispatchers.IO). VpnService callbacks are not coroutines, so the calls are
     * dispatched on this service-scoped supervisor job.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunAndSelf()
            return START_NOT_STICKY
        }
        establishAndStart()
        return START_STICKY
    }

    private fun establishAndStart() {
        if (tun != null) return // already running
        runCatching {
            val builder = Builder()
                .setMtu(MTU)
                .addAddress("172.19.0.1", 30)
                .addDnsServer("172.19.0.2")
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setSession(SESSION)
                // Exclude our own UID: the engine's upstream connections must go
                // out via the real network, otherwise they loop back into tun0
                // and are re-forwarded to our own proxy port (deadlock).
                .addDisallowedApplication(packageName)
            val pfd = builder.establish() ?: error("establish() returned null (consent revoked?)")
            tun = pfd
            val fd = pfd.fd
            serviceScope.launch {
                runCatching { SnishaperEngine.startTun(fd, MTU) }
                    .onFailure { error ->
                        android.util.Log.e("Snishaper", "vpn: startTun failed: ${error.message}", error)
                        stopTunAndSelf()
                    }
            }
        }.onFailure { error ->
            android.util.Log.e("Snishaper", "vpn: establish failed: ${error.message}", error)
            stopTunAndSelf()
        }
    }

    private fun stopTunAndSelf() {
        // NonCancellable: the stop must reach the Go engine even when the service scope
        // is already being cancelled by onDestroy().
        serviceScope.launch {
            withContext(NonCancellable) {
                runCatching { SnishaperEngine.stopTun() }
            }
        }
        runCatching { tun?.close() }
        tun = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        // The Go engine still owns the tun fd: stop it explicitly so the core does not
        // keep a half-closed device alive.
        stopTunAndSelf()
        super.onDestroy()
    }

    override fun onRevoke() {
        // The user revoked the VPN consent from system settings.
        stopTunAndSelf()
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snishaper_logo)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "snishaper_vpn"
        const val NOTIFICATION_ID = 47
        const val MTU = 1500
        const val SESSION = "SniShaper TUN"
        const val ACTION_STOP = "com.snishaper.android.vpn.STOP"
    }
}
