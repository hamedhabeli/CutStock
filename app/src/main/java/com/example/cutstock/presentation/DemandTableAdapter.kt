package com.example.cutstock.presentation

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.cutstock.data.DemandInput

data class DemandRow(
    var lengthStr: String,
    var quantityStr: String
)

class DemandTableAdapter(
    private var rows: MutableList<DemandRow> = mutableListOf(),
    private val onChanged: (List<DemandInput>) -> Unit
) : RecyclerView.Adapter<DemandTableAdapter.ViewHolder>() {

    fun updateData(newDemands: List<DemandInput>) {
        val newRows = newDemands.map { DemandRow(it.lengthMm.toString(), it.quantity.toString()) }.toMutableList()
        if (newRows != rows) {
            rows = newRows
            notifyDataSetChanged()
        }
    }

    fun getDataList(): List<DemandInput> {
        return rows.mapNotNull { row ->
            val length = normalizeAndParse(row.lengthStr)
            val qty = normalizeAndParse(row.quantityStr)
            if (length != null && qty != null && length > 0 && qty > 0) {
                DemandInput(length, qty)
            } else {
                null
            }
        }
    }

    fun addRow() {
        rows.add(DemandRow("", ""))
        notifyItemInserted(rows.size - 1)
        triggerChange()
    }

    fun removeRow(position: Int) {
        if (position in rows.indices) {
            rows.removeAt(position)
            notifyItemRemoved(position)
            triggerChange()
        }
    }

    private fun normalizeAndParse(input: String): Int? {
        val normalized = input.map { c ->
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            }
        }.joinToString("")
        return normalized.trim().toIntOrNull()
    }

    private fun triggerChange() {
        onChanged(getDataList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val linearLayout = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
        }
        val lengthEditText = EditText(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
            hint = "طول (mm)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        val qtyEditText = EditText(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = "تعداد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        linearLayout.addView(lengthEditText)
        linearLayout.addView(qtyEditText)
        return ViewHolder(linearLayout, lengthEditText, qtyEditText)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    inner class ViewHolder(
        view: View,
        val lengthEditText: EditText,
        val qtyEditText: EditText
    ) : RecyclerView.ViewHolder(view) {

        private var lengthWatcher: TextWatcher? = null
        private var qtyWatcher: TextWatcher? = null

        fun bind(row: DemandRow) {
            lengthEditText.removeTextChangedListener(lengthWatcher)
            qtyEditText.removeTextChangedListener(qtyWatcher)

            lengthEditText.setText(row.lengthStr)
            qtyEditText.setText(row.quantityStr)

            lengthWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    row.lengthStr = s?.toString().orEmpty()
                    triggerChange()
                }
            }
            qtyWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    row.quantityStr = s?.toString().orEmpty()
                    triggerChange()
                }
            }

            lengthEditText.addTextChangedListener(lengthWatcher)
            qtyEditText.addTextChangedListener(qtyWatcher)
        }
    }
}
