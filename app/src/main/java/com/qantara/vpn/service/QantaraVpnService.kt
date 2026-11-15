package com.qantara.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats as AndroidTrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.qantara.vpn.QantaraApp
import com.qantara.vpn.R
import com.qantara.vpn.model.ConnectionState
import com.qantara.vpn.model.ServerNode
import com.qantara.vpn.model.TrafficStats
import com.qantara.vpn.parser.UriConfigParser
import com.qantara.vpn.ui.MainActivity
import com.qantara.vpn.util.TrafficFormatter
import com.qantara.vpn.xray.XrayCoreBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class QantaraVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var xrayBridge: XrayCoreBridge? = null
    private var telemetryJob: Job? = null
    private var activeServer: ServerNode? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                val repo = (application as QantaraApp).serverRepository
                val server = repo.getServerById(serverId) ?: repo.getSelectedServer()
                if (server != null) {
                    startVpnTunnel(server)
                }
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
        }
        return START_STICKY
    }

    private fun startVpnTunnel(server: ServerNode) {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            stopVpnTunnel()
        }

        activeServer = server
        _connectionState.value = ConnectionState.CONNECTING
        startForeground(NOTIFICATION_ID, buildNotification(server, TrafficStats()))

        serviceScope.launch {
            try {
                val builder = Builder()
                    .setSession(server.name)
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 30)
                    .addAddress("fd00::2", 126)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addDisallowedApplication(packageName)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                tunInterface = builder.establish()
                val fd = tunInterface?.fd ?: throw IllegalStateException("TUN interface unavailable")

                val xrayJson = UriConfigParser.toXrayJsonConfig(server)
                xrayBridge = XrayCoreBridge(applicationContext, ::protect).also { bridge ->
                    val started = bridge.startCore(xrayJson, fd)
                    if (!started) throw IllegalStateException("Xray-core native binary missing or failed to start")
                }

                _connectionState.value = ConnectionState.CONNECTED
                startTelemetryLoop(server)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                stopVpnTunnel()
            }
        }
    }

    private fun startTelemetryLoop(server: ServerNode) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            var prevRx = AndroidTrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            var prevTx = AndroidTrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            var elapsedSeconds = 0L
            var totalProbes = 0
            var failedProbes = 0

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(1000L)
                elapsedSeconds++

                val coreUp = xrayBridge?.queryStats("proxy", "uplink") ?: -1L
                val coreDown = xrayBridge?.queryStats("proxy", "downlink") ?: -1L

                val curRx = AndroidTrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                val curTx = AndroidTrafficStats.getTotalTxBytes().coerceAtLeast(0L)

                val downRate = if (coreDown >= 0L) coreDown else (curRx - prevRx).coerceAtLeast(0L)
                val upRate = if (coreUp >= 0L) coreUp else (curTx - prevTx).coerceAtLeast(0L)

                prevRx = curRx
                prevTx = curTx

                val pingMs = if (elapsedSeconds % 3L == 1L) {
                    totalProbes++
                    val rtt = measureTcpPing(server.address, server.port)
                    if (rtt < 0) failedProbes++
                    rtt.coerceAtLeast(0)
                } else {
                    _trafficStats.value.pingMs
                }

                val packetLoss = if (totalProbes > 0) {
                    (failedProbes.toFloat() / totalProbes.toFloat()) * 100f
                } else 0f

                val updated = _trafficStats.value.copy(
                    pingMs = pingMs,
                    uploadBytesPerSec = upRate,
                    downloadBytesPerSec = downRate,
                    packetLossPercent = packetLoss,
                    totalUploadBytes = _trafficStats.value.totalUploadBytes + upRate,
                    totalDownloadBytes = _trafficStats.value.totalDownloadBytes + downRate,
                    connectedSeconds = elapsedSeconds
                )
                _trafficStats.value = updated

                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(server, updated))
            }
        }
    }

    private fun measureTcpPing(host: String, port: Int): Int {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1800)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    private fun stopVpnTunnel() {
        telemetryJob?.cancel()
        xrayBridge?.stopCore()
        xrayBridge = null

        runCatching { tunInterface?.close() }
        tunInterface = null

        _trafficStats.value = TrafficStats()
        _connectionState.value = ConnectionState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(server: ServerNode, stats: TrafficStats): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, QantaraVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val content = "↓ ${TrafficFormatter.formatSpeed(stats.downloadBytesPerSec)}  •  ↑ ${TrafficFormatter.formatSpeed(stats.uploadBytesPerSec)}"

        return NotificationCompat.Builder(this, QantaraApp.VPN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_shield)
            .setContentTitle("${server.flagEmoji} ${server.name}")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_disconnect), disconnectIntent)
            .build()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.qantara.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.qantara.vpn.DISCONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"
        private const val NOTIFICATION_ID = 4001

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()
    }
}
