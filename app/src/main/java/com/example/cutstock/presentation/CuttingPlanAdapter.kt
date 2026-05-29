package com.example.cutstock.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cutstock.databinding.ItemCuttingBinBinding
import com.example.cutstock.nativecore.Bin
import kotlin.math.max

class CuttingPlanAdapter : ListAdapter<CuttingBinItem, CuttingPlanAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCuttingBinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemCuttingBinBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CuttingBinItem) {
            binding.binTitleTextView.text = binding.root.context.getString(
                com.example.cutstock.R.string.bin_title_format,
                item.index
            )
            binding.binMetaTextView.text = binding.root.context.getString(
                com.example.cutstock.R.string.bin_meta_format,
                item.bin.stockLengthMm,
                item.bin.usedMm,
                item.bin.wasteMm
            )
            binding.binPiecesTextView.text = item.bin.lengthsMm.joinToString(" + ")

            binding.barContainer.removeAllViews()
            val stockLength = max(item.bin.stockLengthMm.coerceAtLeast(1), 1)
            item.bin.lengthsMm.forEachIndexed { index, lengthMm ->
                val segment = android.view.View(binding.root.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        lengthMm.toFloat()
                    )
                    setBackgroundColor(SEGMENT_COLORS[index % SEGMENT_COLORS.size])
                }
                binding.barContainer.addView(segment)
            }

            val wasteMm = item.bin.wasteMm
            if (wasteMm > 0) {
                val waste = android.view.View(binding.root.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        wasteMm.toFloat()
                    )
                    setBackgroundColor(Color.parseColor("#BDBDBD"))
                }
                binding.barContainer.addView(waste)
            }

            binding.emptyPlanTextView.isVisible = false
        }
    }

    companion object {
        private val SEGMENT_COLORS = intArrayOf(
            Color.parseColor("#1976D2"),
            Color.parseColor("#388E3C"),
            Color.parseColor("#F57C00"),
            Color.parseColor("#7B1FA2"),
            Color.parseColor("#C2185B"),
            Color.parseColor("#00796B")
        )

        private val DiffCallback = object : DiffUtil.ItemCallback<CuttingBinItem>() {
            override fun areItemsTheSame(oldItem: CuttingBinItem, newItem: CuttingBinItem): Boolean =
                oldItem.index == newItem.index

            override fun areContentsTheSame(oldItem: CuttingBinItem, newItem: CuttingBinItem): Boolean =
                oldItem == newItem
        }
    }
}

data class CuttingBinItem(
    val index: Int,
    val stockLengthMm: Int,
    val bin: Bin
)
