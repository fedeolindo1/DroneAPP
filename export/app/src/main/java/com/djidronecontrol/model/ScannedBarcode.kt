package com.djidronecontrol.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a scanned barcode with location and image information
 */
data class ScannedBarcode(
    val barcodeValue: String,
    val barcodeFormat: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,
    val imagePath: String
) {
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getCoordinatesString(): String {
        return String.format(Locale.getDefault(), "%.6f, %.6f, %.1fm", latitude, longitude, altitude)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ScannedBarcode
        
        // Two barcodes are considered equal if they have the same value
        // and are scanned at approximately the same location
        if (barcodeValue != other.barcodeValue) return false
        
        // Check if coordinates are within a small distance (handled elsewhere)
        // This is just a basic check
        val latDiff = Math.abs(latitude - other.latitude)
        val lonDiff = Math.abs(longitude - other.longitude)
        
        return latDiff < 0.00001 && lonDiff < 0.00001
    }
    
    override fun hashCode(): Int {
        var result = barcodeValue.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        return result
    }
}
