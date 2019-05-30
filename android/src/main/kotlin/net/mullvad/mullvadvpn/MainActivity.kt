package net.mullvad.mullvadvpn

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.FragmentActivity

import net.mullvad.mullvadvpn.model.RelaySettings
import net.mullvad.mullvadvpn.model.Settings
import net.mullvad.mullvadvpn.relaylist.RelayItem
import net.mullvad.mullvadvpn.relaylist.RelayList

class MainActivity : FragmentActivity() {
    val activityCreated = CompletableDeferred<Unit>()

    var asyncService = CompletableDeferred<MullvadVpnService>()
    val service
        get() = runBlocking { asyncService.await() }

    val asyncDaemon = startDaemon()
    val daemon
        get() = runBlocking { asyncDaemon.await() }

    var asyncRelayList: Deferred<RelayList> = fetchRelayList()
        private set
    val relayList: RelayList
        get() = runBlocking { asyncRelayList.await() }

    var asyncSettings = fetchSettings()
        private set
    val settings
        get() = runBlocking { asyncSettings.await() }

    var selectedRelayItem: RelayItem? = null

    private val restoreSelectedRelayListItemJob = restoreSelectedRelayListItem()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as MullvadVpnService.LocalBinder

            asyncService.complete(localBinder.service)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            asyncService = CompletableDeferred<MullvadVpnService>()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        activityCreated.complete(Unit)

        if (savedInstanceState == null) {
            addInitialFragment()
        }
    }

    override fun onStart() {
        super.onStart()

        val intent = Intent(this, MullvadVpnService::class.java)

        startService(intent)
        bindService(intent, serviceConnection, 0)
    }

    override fun onStop() {
        unbindService(serviceConnection)

        super.onStop()
    }

    override fun onDestroy() {
        restoreSelectedRelayListItemJob.cancel()
        asyncSettings.cancel()
        asyncRelayList.cancel()
        asyncDaemon.cancel()

        super.onDestroy()
    }

    private fun addInitialFragment() {
        supportFragmentManager?.beginTransaction()?.apply {
            add(R.id.main_fragment, LaunchFragment())
            commit()
        }
    }

    private fun startDaemon() = GlobalScope.async(Dispatchers.Default) {
        activityCreated.await()
        ApiRootCaFile().extract(this@MainActivity)
        MullvadDaemon(asyncService.await())
    }

    private fun fetchRelayList() = GlobalScope.async(Dispatchers.Default) {
        RelayList(asyncDaemon.await().getRelayLocations())
    }

    private fun fetchSettings() = GlobalScope.async(Dispatchers.Default) {
        asyncDaemon.await().getSettings()
    }

    private fun restoreSelectedRelayListItem() = GlobalScope.launch(Dispatchers.Default) {
        val relaySettings = asyncSettings.await().relaySettings

        when (relaySettings) {
            is RelaySettings.CustomTunnelEndpoint -> selectedRelayItem = null
            is RelaySettings.RelayConstraints -> {
                val location = relaySettings.location
                val relayList = asyncRelayList.await()

                selectedRelayItem = relayList.findItemForLocation(location, true)
            }
        }
    }
}
