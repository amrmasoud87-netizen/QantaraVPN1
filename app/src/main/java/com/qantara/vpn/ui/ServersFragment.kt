package com.qantara.vpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.qantara.vpn.QantaraApp
import com.qantara.vpn.databinding.FragmentServersBinding
import com.qantara.vpn.ui.adapter.ServerAdapter
import com.qantara.vpn.util.LocaleHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ServersFragment : Fragment() {

    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo = (requireActivity().application as QantaraApp).serverRepository
        val isArabic = LocaleHelper.isArabic()

        adapter = ServerAdapter(
            isArabic = isArabic,
            onSelect = { server -> repo.selectServer(server.id) },
            onDelete = { server -> repo.removeServer(server.id) }
        )

        binding.recyclerServers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerServers.adapter = adapter

        binding.btnPingAll.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnPingAll.isEnabled = false
                repo.pingAllServers()
                binding.btnPingAll.isEnabled = true
            }
        }

        binding.fabImportServer.setOnClickListener {
            ImportBottomSheetDialog().show(parentFragmentManager, "import_server_sheet")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.servers, repo.selectedServerId) { list, selectedId ->
                    Pair(list, selectedId)
                }.collect { (list, selectedId) ->
                    adapter.submitServers(list, selectedId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
