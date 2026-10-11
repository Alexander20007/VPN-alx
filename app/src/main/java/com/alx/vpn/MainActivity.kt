package com.alx.vpn

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.alx.vpn.core.AlxVpnService
import com.alx.vpn.data.ProfileStore
import com.alx.vpn.data.SettingsStore
import com.alx.vpn.ui.AlxApp
import com.alx.vpn.ui.AlxTheme

class MainActivity : ComponentActivity() {

    private lateinit var store: ProfileStore
    private var pendingId: String? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpnService()
        else Toast.makeText(this, "Permissão de VPN negada", Toast.LENGTH_SHORT).show()
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(this)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            AlxTheme {
                AlxApp(store, SettingsStore(this), onConnect = ::connect, onDisconnect = ::disconnect)
            }
        }
    }

    private fun connect(profileId: String) {
        pendingId = profileId
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpnService()
    }

    private fun startVpnService() {
        val i = Intent(this, AlxVpnService::class.java).putExtra(AlxVpnService.EXTRA_PROFILE, pendingId)
        ContextCompat.startForegroundService(this, i)
    }

    private fun disconnect() {
        startService(Intent(this, AlxVpnService::class.java).setAction(AlxVpnService.ACTION_STOP))
    }
}
