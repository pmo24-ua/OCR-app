package com.ejemplo.ocr

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ThumbAdapter(private val items: List<Uri>) :
    RecyclerView.Adapter<ThumbAdapter.VH>() {

    class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    override fun onCreateViewHolder(p: ViewGroup, vType: Int): VH {
        val iv = LayoutInflater.from(p.context)
            .inflate(R.layout.item_thumb, p, false) as ImageView
        val size = p.measuredWidth / 3 - 8
        iv.layoutParams.width = size; iv.layoutParams.height = size
        return VH(iv)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) =
        h.iv.setImageURI(items[pos])
}
