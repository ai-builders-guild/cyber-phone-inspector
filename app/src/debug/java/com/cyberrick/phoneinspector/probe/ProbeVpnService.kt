package com.cyberrick.phoneinspector.probe

import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Opens a tun interface with a single unroutable /32, runs the owner uid lookup while this app is
 * the active VPN (the only time Android allows it), then closes the tun. Carries no traffic.
 */
class ProbeVpnService : VpnService() {
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Thread { runAndRecord() }.start()
        return START_NOT_STICKY
    }

    private fun runAndRecord() {
        val results = mutableListOf<ProbeResult>()
        val start = System.nanoTime()
        var fd: ParcelFileDescriptor? = null
        try {
            fd =
                Builder()
                    .setSession("CPI probe")
                    .addAddress("10.77.77.2", 32)
                    .addRoute("10.77.77.1", 32)
                    .setBlocking(false)
                    .establish()
            results +=
                ProbeResult(
                    api = "VpnService.Builder.establish",
                    count = if (fd != null) 1 else 0,
                    fieldPresent = fd != null,
                    elapsedMs = (System.nanoTime() - start) / 1_000_000,
                    note = "count 1 = tun opened",
                )
            results += ownerUidWhileVpnActive()
        } catch (e: Exception) {
            results += ProbeResult(api = "VpnService.Builder.establish", error = "${e.javaClass.name}: ${e.message}")
        } finally {
            fd?.close()
        }
        File(filesDir, VPN_RESULT_FILE).writeText(ProbeRun(ProbeRunner(this).device(), results).toJson(), Charsets.UTF_8)
        stopSelf()
    }

    /**
     * The VPN app's own sockets are excluded from its tunnel, so the lookup must target another uid.
     * Listens on loopback for a connection from any other process (the adb shell during the probe)
     * and resolves that peer's uid.
     */
    private fun ownerUidWhileVpnActive(): ProbeResult {
        val api = "ConnectivityManager.getConnectionOwnerUid(TCP from another uid) while VPN active"
        val start = System.nanoTime()
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            ServerSocket(PEER_PORT, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = PEER_WAIT_MS
                server.accept().use { accepted ->
                    val peerLocal = accepted.remoteSocketAddress as InetSocketAddress
                    val peerRemote = accepted.localSocketAddress as InetSocketAddress
                    val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, peerLocal, peerRemote)
                    ProbeResult(
                        api = api,
                        count = if (uid != Process.INVALID_UID && uid != Process.myUid()) 1 else 0,
                        fieldPresent = uid != Process.INVALID_UID,
                        elapsedMs = (System.nanoTime() - start) / 1_000_000,
                        note = "count 1 = resolved to a uid other than our own; peer was the adb shell",
                    )
                }
            }
        } catch (e: Exception) {
            ProbeResult(api = api, error = "${e.javaClass.name}: ${e.message}")
        }
    }

    companion object {
        const val VPN_RESULT_FILE = "probe_vpn.json"
        const val PEER_PORT = 27077
        const val PEER_WAIT_MS = 20_000
    }
}
