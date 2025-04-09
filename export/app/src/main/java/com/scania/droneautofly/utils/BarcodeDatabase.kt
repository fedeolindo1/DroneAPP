package com.scania.droneautofly.utils

import com.scania.droneautofly.model.ScannedBarcode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton class para armazenar e gerenciar os códigos de barras escaneados
 * Esta é uma implementação em memória que seria substituída por um banco de dados real em produção
 */
class BarcodeDatabase private constructor() {
    
    private val barcodes = CopyOnWriteArrayList<ScannedBarcode>()
    
    // Adiciona um novo código de barras ao banco de dados
    fun addBarcode(barcode: ScannedBarcode) {
        // Verifica se um código de barras similar já existe na mesma localização aproximada
        val exists = barcodes.any { 
            it.barcodeValue == barcode.barcodeValue && 
            isNearby(it.latitude, it.longitude, barcode.latitude, barcode.longitude) 
        }
        
        if (!exists) {
            barcodes.add(barcode)
        }
    }
    
    // Obtém todos os códigos de barras
    fun getAllBarcodes(): List<ScannedBarcode> {
        return barcodes.toList()
    }
    
    // Obtém um código de barras pelo seu valor
    fun getBarcodeByValue(value: String): ScannedBarcode? {
        return barcodes.firstOrNull { it.barcodeValue == value }
    }
    
    // Obtém códigos de barras pelo formato
    fun getBarcodesByFormat(format: String): List<ScannedBarcode> {
        return barcodes.filter { it.barcodeFormat == format }
    }
    
    // Limpa todos os códigos de barras
    fun clearAll() {
        barcodes.clear()
    }
    
    // Verifica se duas coordenadas estão próximas uma da outra (dentro de 2 metros)
    private fun isNearby(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
        // Conversão aproximada: 1 grau de latitude = 111km
        // Vamos considerar "próximo" como dentro de 2 metros
        val latDiff = Math.abs(lat1 - lat2) * 111000 // em metros
        val lonDiff = Math.abs(lon1 - lon2) * 111000 * Math.cos(Math.toRadians(lat1)) // em metros
        
        val distance = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff)
        return distance < 2.0 // 2 metros
    }
    
    companion object {
        @Volatile
        private var instance: BarcodeDatabase? = null
        
        fun getInstance(): BarcodeDatabase {
            return instance ?: synchronized(this) {
                instance ?: BarcodeDatabase().also { instance = it }
            }
        }
    }
}