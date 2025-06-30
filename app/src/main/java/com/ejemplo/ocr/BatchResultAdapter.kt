package com.ejemplo.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.RecyclerView

class BatchResultAdapter(
    private val ctx: Context,
    private val items: List<String>
) : RecyclerView.Adapter<BatchResultAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIdx : TextView   = v.findViewById(R.id.tvIndex)
        val tvTxt : TextView   = v.findViewById(R.id.tvText)
        val btnCopy: ImageButton = v.findViewById(R.id.btnCopy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tvIdx.text = ctx.getString(R.string.result_n, position + 1)
        holder.tvTxt.text = items[position]

        holder.btnCopy.setOnClickListener {
            val clip = ClipData.newPlainText("OCR", items[position])
            ctx.getSystemService<ClipboardManager>()
                ?.setPrimaryClip(clip)
            toast(ctx, ctx.getString(R.string.copiado))
        }
    }

    private fun toast(c: Context, msg:String) =
        android.widget.Toast.makeText(c, msg,
            android.widget.Toast.LENGTH_SHORT).show()
}
