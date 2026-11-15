package com.qantara.vpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.qantara.vpn.QantaraApp
import com.qantara.vpn.R
import com.qantara.vpn.databinding.FragmentHomeBinding
import com.qantara.vpn.model.ConnectionState
import com.qantara.vpn.service.QantaraVpnService
import com.qantara.vpn.util.LocaleHelper
import com.qantara.vpn.util.TrafficFormatter
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo = (requireActivity().application as QantaraApp).serverRepository
        val isArabic = LocaleHelper.isArabic()

        binding.btnLanguageToggle.text = if (isArabic) "EN" else "العربية"
        binding.btnLanguageToggle.setOnClickListener {
            LocaleHelper.toggleLanguage()
        }

        binding.btnConnect.setOnClickListener {
            (requireActivity() as MainActivity).toggleVpnConnection()
        }

        binding.cardActiveServer.setOnClickListener {
            (requireActivity() as MainActivity).openServersTab()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repo.selectedServerId.collect {
                        val server = repo.getSelectedServer()
                        if (server != null) {
                            binding.tvServerFlag.text = server.flagEmoji
                            binding.tvServerName.text = server.localizedCountry(isArabic)
                            binding.tvServerCity.text = "${server.localizedCity(isArabic)} · ${server.protocol.displayName} (${server.transport.displayName})"
                        }
                    }
                }

                launch {
                    QantaraVpnService.connectionState.collect { state ->
                        updateConnectionUi(state)
                    }
                }

                launch {
                    QantaraVpnService.trafficStats.collect { stats ->
                        binding.tvPingValue.text = if (stats.pingMs > 0) "${stats.pingMs} ms" else "—"
                        binding.tvDownloadValue.text = TrafficFormatter.formatSpeed(stats.downloadBytesPerSec)
                        binding.tvUploadValue.text = TrafficFormatter.formatSpeed(stats.uploadBytesPerSec)
                        binding.tvPacketLossValue.text = String.format(Locale.US, "%.1f%%", stats.packetLossPercent)
                        binding.tvSessionTimer.text = TrafficFormatter.formatDuration(stats.connectedSeconds)
                    }
                }
            }
        }
    }

    private fun updateConnectionUi(state: ConnectionState) {
        val ctx = requireContext()
        when (state) {
            ConnectionState.CONNECTED -> {
                binding.tvConnectionStatus.text = getString(R.string.status_connected)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_connected))
                binding.btnConnect.text = getString(R.string.action_disconnect)
                binding.btnConnect.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_connected))
                binding.btnConnect.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onPrimary))
            }
            ConnectionState.CONNECTING -> {
                binding.tvConnectionStatus.text = getString(R.string.status_connecting)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_connecting))
                binding.btnConnect.text = getString(R.string.status_connecting)
                binding.btnConnect.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_connecting))
            }
            ConnectionState.ERROR -> {
                binding.tvConnectionStatus.text = getString(R.string.status_error)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_error))
                binding.btnConnect.text = getString(R.string.action_connect)
                binding.btnConnect.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_primary))
            }
            else -> {
                binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onSurfaceVariant))
                binding.btnConnect.text = getString(R.string.action_connect)
                binding.btnConnect.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_primary))
                binding.btnConnect.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onPrimary))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
