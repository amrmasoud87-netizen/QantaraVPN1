package com.qantara.vpn.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qantara.vpn.R
import com.qantara.vpn.databinding.ItemServerBinding
import com.qantara.vpn.model.ServerNode

class ServerAdapter(
    private val isArabic: Boolean,
    private val onSelect: (ServerNode) -> Unit,
    private val onDelete: (ServerNode) -> Unit
) : ListAdapter<ServerNode, ServerAdapter.ServerViewHolder>(DiffCallback) {

    private var selectedId: String? = null

    fun submitServers(items: List<ServerNode>, activeId: String?) {
        selectedId = activeId
        submitList(items) { notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(private val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(node: ServerNode) {
            val ctx = binding.root.context
            val isSelected = node.id == selectedId

            binding.tvFlag.text = node.flagEmoji
            binding.tvCountryName.text = node.localizedCountry(isArabic)
            binding.tvServerMeta.text =
                "${node.localizedCity(isArabic)} · ${node.protocol.displayName} / ${node.transport.displayName} / ${node.security.displayName}"

            binding.tvPing.text = if (node.pingMs > 0) "${node.pingMs} ms" else "—"
            val pingColor = when {
                node.pingMs in 1..60 -> R.color.status_connected
                node.pingMs in 61..130 -> R.color.status_connecting
                node.pingMs > 130 -> R.color.status_error
                else -> R.color.md_theme_onSurfaceVariant
            }
            binding.tvPing.setTextColor(ContextCompat.getColor(ctx, pingColor))

            binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.cardServer.strokeColor = ContextCompat.getColor(
                ctx,
                if (isSelected) R.color.md_theme_primary else R.color.md_theme_outlineVariant
            )

            binding.btnDeleteServer.visibility = if (node.isCustom) View.VISIBLE else View.GONE
            binding.btnDeleteServer.setOnClickListener { onDelete(node) }
            binding.root.setOnClickListener { onSelect(node) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ServerNode>() {
        override fun areItemsTheSame(oldItem: ServerNode, newItem: ServerNode): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ServerNode, newItem: ServerNode): Boolean = oldItem == newItem
    }
}
