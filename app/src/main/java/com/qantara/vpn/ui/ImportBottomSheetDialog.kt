package com.qantara.vpn.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.qantara.vpn.QantaraApp
import com.qantara.vpn.R
import com.qantara.vpn.databinding.BottomSheetImportBinding
import com.qantara.vpn.parser.UriConfigParser

class ImportBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImportBinding? = null
    private val binding get() = _binding!!

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val raw = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_RESULT).orEmpty()
            importRawConfig(raw)
        }
    }

    private val jsonFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()?.let { jsonContent ->
                importRawConfig(jsonContent)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnImportQr.setOnClickListener {
            qrLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
        }

        binding.btnImportJsonFile.setOnClickListener {
            jsonFileLauncher.launch(arrayOf("application/json", "text/plain"))
        }

        binding.btnImportLink.setOnClickListener {
            val raw = binding.etConfigInput.text?.toString().orEmpty()
            importRawConfig(raw)
        }
    }

    private fun importRawConfig(raw: String) {
        if (raw.isBlank()) {
            Toast.makeText(requireContext(), R.string.import_empty_error, Toast.LENGTH_SHORT).show()
            return
        }

        UriConfigParser.parseInput(raw)
            .onSuccess { node ->
                (requireActivity().application as QantaraApp).serverRepository.addServer(node)
                Toast.makeText(requireContext(), getString(R.string.import_success, node.name), Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .onFailure { err ->
                Toast.makeText(requireContext(), err.message ?: getString(R.string.import_failed), Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
