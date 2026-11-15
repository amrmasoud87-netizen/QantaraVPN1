package com.qantara.vpn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.qantara.vpn.QantaraApp
import com.qantara.vpn.R
import com.qantara.vpn.databinding.ActivityMainBinding
import com.qantara.vpn.model.ConnectionState
import com.qantara.vpn.service.QantaraVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startSelectedVpn()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null) {
            switchFragment(HomeFragment())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(HomeFragment())
                    true
                }
                R.id.nav_servers -> {
                    switchFragment(ServersFragment())
                    true
                }
                else -> false
            }
        }
    }

    fun toggleVpnConnection() {
        val currentState = QantaraVpnService.connectionState.value
        if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING) {
            val intent = Intent(this, QantaraVpnService::class.java).apply {
                action = QantaraVpnService.ACTION_DISCONNECT
            }
            startService(intent)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startSelectedVpn()
            }
        }
    }

    private fun startSelectedVpn() {
        val server = (application as QantaraApp).serverRepository.getSelectedServer() ?: return
        val intent = Intent(this, QantaraVpnService::class.java).apply {
            action = QantaraVpnService.ACTION_CONNECT
            putExtra(QantaraVpnService.EXTRA_SERVER_ID, server.id)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun openServersTab() {
        binding.bottomNavigation.selectedItemId = R.id.nav_servers
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
