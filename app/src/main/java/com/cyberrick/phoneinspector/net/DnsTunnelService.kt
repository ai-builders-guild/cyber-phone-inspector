package com.cyberrick.phoneinspector.net

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.cyberrick.phoneinspector.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * The network monitor. A VpnService whose only route is its own resolver address, so the phone stays online and
 * only DNS transits the tun. Queries are relayed to the real upstream resolver through a protected socket and
 * attributed to the app that sent them. Runs for a bounded window, then saves a [MonitorResult] and stops.
 * Carries no payload traffic and stores no packet.
 */
class DnsTunnelService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var upstream: DatagramSocket? = null
    private var upstreamServers: List<InetAddress> = emptyList()
    private lateinit var relay: DnsRelay
    private val flows = mutableListOf<com.cyberrick.phoneinspector.core.model.FlowRecord>()
    private val lock = Any()

    @Volatile private var running = false
    private var startedAt = 0L
    private var endsAt = 0L
    private var windowMinutes = MonitorWindow.DEFAULT_MINUTES

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        windowMinutes = MonitorWindow.clamp(intent?.getIntExtra(EXTRA_MINUTES, MonitorWindow.DEFAULT_MINUTES))
        val refusal = preflight()
        if (refusal != null) {
            Log.w(TAG, "refused: $refusal")
            // Started with startForegroundService, so Android requires a startForeground call before the service stops.
            goForeground()
            state.value = MonitorState.Refused(refusal)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startTunnel()
        } catch (e: Exception) {
            Log.e(TAG, "tunnel failed", e)
            state.value = MonitorState.Refused("${e.javaClass.simpleName}: ${e.message}")
            closeQuietly()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Strict private DNS and an unknown upstream resolver are the two reasons not to start. */
    private fun preflight(): String? {
        val cm = getSystemService(ConnectivityManager::class.java)
        val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return getString(R.string.monitor_no_network)
        when (val d = PrivateDnsPolicy.decide(lp.isPrivateDnsActive, lp.privateDnsServerName)) {
            is PrivateDnsPolicy.Decision.RefuseStrict -> return d.message
            PrivateDnsPolicy.Decision.Allow -> {}
        }
        upstreamServers = lp.dnsServers.sortedBy { if (it is Inet4Address) 0 else 1 }
        if (upstreamServers.isEmpty()) return getString(R.string.monitor_no_dns)
        return null
    }

    private fun startTunnel() {
        relay = DnsRelay(AndroidUidResolver(this), RESOLVER)
        val fd =
            Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(CLIENT, 32)
                .addRoute(RESOLVER, 32)
                .addDnsServer(RESOLVER)
                .setMtu(MTU)
                .setBlocking(true)
                .establish() ?: throw IllegalStateException("establish() returned null; consent missing?")
        tun = fd
        val socket =
            DatagramSocket().also {
                protect(it)
                it.soTimeout = 1000
            }
        upstream = socket
        startedAt = System.currentTimeMillis()
        endsAt = startedAt + windowMinutes * 60_000L
        running = true
        flows.clear()
        state.value = MonitorState.Running(startedAt, endsAt, 0)
        goForeground()
        Log.i(TAG, "monitor started for $windowMinutes min, upstream ${upstreamServers.size} servers")
        thread(name = "cpi-tun-reader") { tunLoop(fd, socket) }
        thread(name = "cpi-upstream-reader") { upstreamLoop(fd, socket) }
        thread(name = "cpi-ticker") { ticker() }
    }

    private fun tunLoop(
        fd: ParcelFileDescriptor,
        socket: DatagramSocket,
    ) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(MTU)
        while (running) {
            val n =
                try {
                    input.read(buf)
                } catch (e: Exception) {
                    if (running) Log.w(TAG, "tun read ended: ${e.message}")
                    break
                }
            if (n <= 0) continue
            val packet = buf.copyOf(n)
            val action = synchronized(lock) { relay.onTunPacket(packet) }
            when (action) {
                is DnsRelay.Action.Forward -> {
                    val server = upstreamServers.first()
                    try {
                        socket.send(DatagramPacket(action.payload, action.payload.size, InetSocketAddress(server, DnsRelay.DNS_PORT)))
                    } catch (e: Exception) {
                        Log.w(TAG, "upstream send failed: ${e.message}")
                    }
                }
                is DnsRelay.Action.WriteToTun -> writeTun(output, action.packet)
                DnsRelay.Action.Drop -> {}
            }
        }
    }

    private fun upstreamLoop(
        fd: ParcelFileDescriptor,
        socket: DatagramSocket,
    ) {
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(4096)
        while (running) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (running) Log.w(TAG, "upstream receive ended: ${e.message}")
                break
            }
            val payload = buf.copyOf(packet.length)
            val out = synchronized(lock) { relay.onUpstreamResponse(payload) }
            out.tunPacket?.let { writeTun(output, it) }
            out.flow?.let { flow ->
                synchronized(lock) { flows += flow }
                state.value = MonitorState.Running(startedAt, endsAt, flows.size)
            }
        }
    }

    private fun writeTun(
        output: FileOutputStream,
        packet: ByteArray,
    ) {
        try {
            output.write(packet)
        } catch (e: Exception) {
            if (running) Log.w(TAG, "tun write failed: ${e.message}")
        }
    }

    private fun ticker() {
        while (running) {
            Thread.sleep(1000)
            synchronized(lock) { if (running) relay.expirePending() }
            if (running && System.currentTimeMillis() >= endsAt) finish()
        }
    }

    @Synchronized
    private fun finish() {
        if (!running) {
            closeQuietly()
            stopSelf()
            return
        }
        running = false
        val endedAt = System.currentTimeMillis()
        val counters =
            try {
                NetStatsCollector(AndroidNetStatsSource(this), AndroidUidResolver(this)).collect(startedAt, endedAt)
            } catch (e: Exception) {
                Log.w(TAG, "net stats unavailable: ${e.message}")
                emptyList()
            }
        val result =
            synchronized(lock) {
                MonitorResult(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    windowMinutes = windowMinutes,
                    flows = flows.toList() + counters,
                    lookups = relay.stats.flows,
                    medianLatencyMs = MonitorWindow.median(relay.stats.latenciesMs),
                    droppedMalformed = relay.stats.droppedMalformed,
                    resetsSent = relay.stats.resetsSent,
                )
            }
        FlowStore(filesDir).save(result)
        Log.i(TAG, "monitor finished: ${result.lookups} lookups, median ${result.medianLatencyMs} ms, ${result.resetsSent} resets")
        closeQuietly()
        state.value = MonitorState.Finished(result)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeQuietly() {
        running = false
        try {
            upstream?.close()
        } catch (e: Exception) {
            Log.w(TAG, "socket close: ${e.message}")
        }
        try {
            tun?.close()
        } catch (e: Exception) {
            Log.w(TAG, "tun close: ${e.message}")
        }
        upstream = null
        tun = null
    }

    override fun onRevoke() {
        Log.i(TAG, "revoked by the system or the user")
        finish()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "app task removed; stopping the monitor")
        finish()
    }

    override fun onDestroy() {
        closeQuietly()
        if (state.value is MonitorState.Running) state.value = MonitorState.Idle
        super.onDestroy()
    }

    @SuppressLint("ForegroundServiceType") // specialUse is declared in the manifest; lint does not recognise it
    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitor_notification, windowMinutes))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "CPIMonitor"
        const val RESOLVER = "10.77.77.1"
        const val CLIENT = "10.77.77.2"
        private const val MTU = 1500
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 2
        const val EXTRA_MINUTES = "minutes"
        const val ACTION_STOP = "com.cyberrick.phoneinspector.MONITOR_STOP"

        val state: MutableStateFlow<MonitorState> = MutableStateFlow(MonitorState.Idle)
        val stateFlow: StateFlow<MonitorState> get() = state

        fun start(
            context: Context,
            minutes: Int,
        ) {
            context.startForegroundService(Intent(context, DnsTunnelService::class.java).putExtra(EXTRA_MINUTES, minutes))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsTunnelService::class.java).setAction(ACTION_STOP))
        }
    }
}
