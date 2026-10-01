package ru.prorabprime.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/** Calls [onAvailable] whenever the phone gets a network, so a send need not wait for the next round. */
class AndroidConnectivityMonitor(
    private val context: Context,
    private val onAvailable: () -> Unit,
) {
    fun start() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onAvailable()
            },
        )
    }
}
