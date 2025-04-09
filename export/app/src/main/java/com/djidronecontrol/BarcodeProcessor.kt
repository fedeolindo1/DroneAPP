package com.djidronecontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.djidronecontrol.model.ScannedBarcode
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap
import java.util.EnumSet
import java.util.UUID

class BarcodeProcessor {
    private val TAG = "BarcodeProcessor"
    private val scannedBarcodes = mutableListOf<ScannedBarcode>()
    private val reader = MultiFormatReader()
    
    init {
        // Configure the barcode reader with hints
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = EnumSet.of(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.ITF
        )
        hints[DecodeHintType.TRY_HARDER] = true
        reader.setHints(hints)
    }

    fun processImage(bitmap: Bitmap, latitude: Double, longitude: Double, altitude: Double): List<ScannedBarcode> {
        try {
            // Convert bitmap to binary bitmap for ZXing
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            try {
                val result = reader.decode(binaryBitmap)
                
                // Create a new scanned barcode object
                val barcode = ScannedBarcode(
                    barcodeValue = result.text,
                    barcodeFormat = result.barcodeFormat.toString(),
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    timestamp = System.currentTimeMillis(),
                    imagePath = saveImage(bitmap)
                )
                
                // Check if barcode was already scanned
                if (!isDuplicateBarcode(barcode)) {
                    scannedBarcodes.add(barcode)
                    Log.i(TAG, "New barcode detected: ${barcode.barcodeValue}")
                    return listOf(barcode)
                } else {
                    Log.i(TAG, "Duplicate barcode detected: ${barcode.barcodeValue}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding barcode: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}")
        }
        
        return emptyList()
    }
    
    private fun saveImage(bitmap: Bitmap): String {
        val fileName = "barcode_${UUID.randomUUID()}.jpg"
        val storageDir = File(DJIApplication.getContext().getExternalFilesDir(null), "DroneMission")
        
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val imageFile = File(storageDir, fileName)
        
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return imageFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
            return ""
        }
    }
    
    private fun isDuplicateBarcode(newBarcode: ScannedBarcode): Boolean {
        return scannedBarcodes.any { it.barcodeValue == newBarcode.barcodeValue }
    }
    
    fun getScannedBarcodes(): List<ScannedBarcode> {
        return scannedBarcodes.toList()
    }
    
    fun clearScannedBarcodes() {
        scannedBarcodes.clear()
    }
    
    companion object {
        // Check if two coordinates are close to each other (within ~2 meters)
        fun areCoordinatesClose(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
            val earthRadius = 6371000.0 // meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            val distance = earthRadius * c
            
            return distance < 2.0 // Within 2 meters
        }
    }
}
