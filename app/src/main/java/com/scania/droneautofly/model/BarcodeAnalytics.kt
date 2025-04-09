package com.scania.droneautofly.model

/**
 * Data class representing analytics information for scanned barcodes
 */
data class BarcodeAnalytics(
    val totalScanned: Int,            // Total number of barcodes scanned
    val uniqueBarcodes: Int,          // Number of unique barcode values
    val mostCommonFormat: String?,    // Most common barcode format
    val scanRate: Double              // Scan rate (barcodes per minute)
)