package com.example.cutstock.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cutstock.databinding.ItemCuttingBinBinding
import com.example.cutstock.nativecore.Bin
import kotlin.math.max

data class GroupedBinItem(
    val patternIndex: Int,        // 1-based display number
    val pieces: IntArray,          // sorted piece lengths
    val stockLengthMm: Int,
    val usedMm: Int,
    val wasteMm: Int,
    val repeatCount: Int,           // how many bars have this exact pattern
    val originalIndices: List<Int>  // original 1-based indices of matching bars
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GroupedBinItem
        return patternIndex == other.patternIndex &&
            pieces.contentEquals(other.pieces) &&
            stockLengthMm == other.stockLengthMm &&
            usedMm == other.usedMm &&
            wasteMm == other.wasteMm &&
            repeatCount == other.repeatCount &&
            originalIndices == other.originalIndices
    }

    override fun hashCode(): Int {
        var result = patternIndex
        result = 31 * result + pieces.contentHashCode()
        result = 31 * result + stockLengthMm
        result = 31 * result + usedMm
        result = 31 * result + wasteMm
        result = 31 * result + repeatCount
        result = 31 * result + originalIndices.hashCode()
        return result
    }
}

class CuttingPlanAdapter : ListAdapter<Bin, CuttingPlanAdapter.ViewHolder>(DiffCallback) {

    private var groupedItems: List<GroupedBinItem> = emptyList()
    private val expandedPatterns = mutableSetOf<Int>()

    override fun submitList(list: List<Bin>?) {
        if (list == null) {
            groupedItems = emptyList()
            super.submitList(emptyList())
            return
        }
        groupedItems = groupBins(list)
        super.submitList(list)
    }

    override fun getItemCount(): Int = groupedItems.size

    private fun getGroupedItem(position: Int): GroupedBinItem = groupedItems[position]

    private fun groupBins(bins: List<Bin>): List<GroupedBinItem> {
        val groups = mutableListOf<MutableList<Pair<Int, Bin>>>()
        bins.forEachIndexed { index, bin ->
            val sortedPieces = bin.lengthsMm.sorted()
            val group = groups.find { g ->
                val firstBin = g.first().second
                firstBin.stockLengthMm == bin.stockLengthMm &&
                    firstBin.usedMm == bin.usedMm &&
                    firstBin.wasteMm == bin.wasteMm &&
                    firstBin.lengthsMm.sorted() == sortedPieces
            }
            if (group != null) {
                group.add((index + 1) to bin)
            } else {
                groups.add(mutableListOf((index + 1) to bin))
            }
        }

        return groups.mapIndexed { i, group ->
            val firstBin = group.first().second
            GroupedBinItem(
                patternIndex = i + 1,
                pieces = firstBin.lengthsMm.sorted().toIntArray(),
                stockLengthMm = firstBin.stockLengthMm,
                usedMm = firstBin.usedMm,
                wasteMm = firstBin.wasteMm,
                repeatCount = group.size,
                originalIndices = group.map { it.first }
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCuttingBinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getGroupedItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCuttingBinBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val patternIndex = getGroupedItem(position).patternIndex
                    if (expandedPatterns.contains(patternIndex)) {
                        expandedPatterns.remove(patternIndex)
                    } else {
                        expandedPatterns.add(patternIndex)
                    }
                    notifyItemChanged(position)
                }
            }
        }

        fun bind(item: GroupedBinItem) {
            binding.binTitleTextView.text = "الگو ${item.patternIndex} — ×${item.repeatCount} میلگرد"
            binding.binMetaTextView.text = "طول ${item.stockLengthMm} mm | استفاده: ${item.usedMm} | ضایعات: ${item.wasteMm} mm"
            binding.binPiecesTextView.text = item.pieces.joinToString(" + ")

            binding.barContainer.removeAllViews()
            val stockLength = max(item.stockLengthMm.coerceAtLeast(1), 1)
            item.pieces.forEachIndexed { index, lengthMm ->
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

            val wasteMm = item.wasteMm
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

            // Programmatic Expandable Details View
            val innerLayout = binding.root.getChildAt(0) as ViewGroup
            var detailsView = binding.root.findViewWithTag<TextView>("details_view_tag")
            if (detailsView == null) {
                detailsView = TextView(binding.root.context).apply {
                    tag = "details_view_tag"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 16
                    }
                    textSize = 13f
                    setTextColor(Color.parseColor("#757575"))
                    textDirection = View.TEXT_DIRECTION_LOCALE
                }
                innerLayout.addView(detailsView)
            }

            val isExpanded = expandedPatterns.contains(item.patternIndex)
            detailsView.isVisible = isExpanded
            if (isExpanded) {
                detailsView.text = "جزئیات شاخه‌ها: " + item.originalIndices.map { "شاخه $it" }.joinToString("، ")
            }
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

        private val DiffCallback = object : DiffUtil.ItemCallback<Bin>() {
            override fun areItemsTheSame(oldItem: Bin, newItem: Bin): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: Bin, newItem: Bin): Boolean =
                oldItem == newItem
        }
    }
}
