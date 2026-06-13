package com.example.cutstock.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cutstock.databinding.ItemProjectBinding
import java.text.DateFormat
import java.util.Date

class ProjectListAdapter(
    private val onOpen: (Long) -> Unit,
    private val onDelete: (Long) -> Unit
) : ListAdapter<ProjectListItem, ProjectListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onOpen, onDelete)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemProjectBinding,
        private val onOpen: (Long) -> Unit,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProjectListItem) {
            binding.projectNameTextView.text = item.name
            binding.projectMetaTextView.text = binding.root.context.getString(
                com.example.cutstock.R.string.project_meta_format,
                item.maxStockLengthMm,
                item.demandCount,
                if (item.hasPlan) {
                    binding.root.context.getString(com.example.cutstock.R.string.plan_ready)
                } else {
                    binding.root.context.getString(com.example.cutstock.R.string.plan_missing)
                }
            )
            binding.projectUpdatedTextView.text = binding.root.context.getString(
                com.example.cutstock.R.string.project_updated_format,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(item.updatedAtMillis))
            )
            binding.root.setOnClickListener { onOpen(item.id) }
            binding.deleteButton.setOnClickListener { onDelete(item.id) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ProjectListItem>() {
            override fun areItemsTheSame(oldItem: ProjectListItem, newItem: ProjectListItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ProjectListItem, newItem: ProjectListItem): Boolean =
                oldItem == newItem
        }
    }
}
