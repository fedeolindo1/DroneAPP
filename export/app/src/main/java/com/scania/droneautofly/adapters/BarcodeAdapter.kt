package com.scania.droneautofly.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scania.droneautofly.R
import com.scania.droneautofly.model.ScannedBarcode
import java.io.File

/**
 * Adapter for displaying scanned barcodes in a RecyclerView
 */
class BarcodeAdapter(
    private val barcodes: List<ScannedBarcode>
) : RecyclerView.Adapter<BarcodeAdapter.BarcodeViewHolder>() {

    class BarcodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBarcodeValue: TextView = itemView.findViewById(R.id.tv_barcode_value)
        val tvBarcodeFormat: TextView = itemView.findViewById(R.id.tv_barcode_format)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tv_timestamp)
        val imgBarcode: ImageView = itemView.findViewById(R.id.img_barcode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarcodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_barcode, parent, false)
        return BarcodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BarcodeViewHolder, position: Int) {
        val barcode = barcodes[position]

        holder.tvBarcodeValue.text = barcode.barcodeValue
        holder.tvBarcodeFormat.text = barcode.barcodeFormat
        holder.tvLocation.text = barcode.getCoordinatesString()
        holder.tvTimestamp.text = barcode.getFormattedTimestamp()

        // Carrega a imagem do código de barras
        val imageFile = File(barcode.imagePath)
        if (imageFile.exists()) {
            // Em uma implementação real, usaríamos Glide ou Picasso para carregar a imagem
            // holder.imgBarcode.setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
            holder.imgBarcode.setImageResource(R.drawable.ic_barcode_placeholder)
        } else {
            holder.imgBarcode.setImageResource(R.drawable.ic_barcode_placeholder)
        }
    }

    override fun getItemCount(): Int = barcodes.size
}