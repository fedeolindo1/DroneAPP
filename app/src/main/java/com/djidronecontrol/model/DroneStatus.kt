package com.djidronecontrol.model

/**
 * Data class representing the current status of the drone
 */
data class DroneStatus(
    val batteryPercent: Double = 0.0,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isFlying: Boolean = false,
    val flightMode: String = "UNKNOWN",
    val velocity: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0
) {
    fun getSpeed(): Double {
        // Calculate the horizontal speed from X and Y velocity components
        return Math.sqrt(velocity.first * velocity.first + velocity.second * velocity.second)
    }
    
    fun getVerticalSpeed(): Double {
        // Return the vertical speed (Z velocity component)
        return velocity.third
    }
    
    fun getDistanceToHome(): Double {
        if (homeLatitude == 0.0 && homeLongitude == 0.0) {
            return 0.0
        }
        
        // Calculate distance to home using Haversine formula
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(latitude - homeLatitude)
        val dLon = Math.toRadians(longitude - homeLongitude)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(homeLatitude)) * Math.cos(Math.toRadians(latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    fun isHomeLocationSet(): Boolean {
        return homeLatitude != 0.0 || homeLongitude != 0.0
    }
}
