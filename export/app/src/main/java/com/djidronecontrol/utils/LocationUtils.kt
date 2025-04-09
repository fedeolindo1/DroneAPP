package com.djidronecontrol.utils

import android.location.Location
import dji.common.flightcontroller.LocationCoordinate3D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility class for handling location-related operations
 */
object LocationUtils {
    
    private const val EARTH_RADIUS = 6371000.0 // Earth radius in meters
    
    /**
     * Calculate distance between two coordinates in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS * c
    }
    
    /**
     * Calculate bearing (angle) between two coordinates in degrees
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        
        return bearing
    }
    
    /**
     * Calculate new coordinates based on distance and bearing from starting point
     */
    fun calculateCoordinates(lat: Double, lon: Double, distance: Double, bearing: Double): Pair<Double, Double> {
        val distRatio = distance / EARTH_RADIUS
        val bearingRad = Math.toRadians(bearing)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        
        val newLatRad = asin(sin(latRad) * cos(distRatio) +
                cos(latRad) * sin(distRatio) * cos(bearingRad))
        
        val newLonRad = lonRad + atan2(sin(bearingRad) * sin(distRatio) * cos(latRad),
                cos(distRatio) - sin(latRad) * sin(newLatRad))
        
        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }
    
    /**
     * Convert LocationCoordinate3D to android Location object
     */
    fun locationCoordinate3DToLocation(coordinate: LocationCoordinate3D): Location {
        val location = Location("drone")
        location.latitude = coordinate.latitude
        location.longitude = coordinate.longitude
        location.altitude = coordinate.altitude.toDouble()
        return location
    }
    
    /**
     * Check if two locations are close to each other (within the specified distance in meters)
     */
    fun areLocationsClose(lat1: Double, lon1: Double, lat2: Double, lon2: Double, maxDistanceMeters: Double): Boolean {
        val distance = calculateDistance(lat1, lon1, lat2, lon2)
        return distance <= maxDistanceMeters
    }
    
    /**
     * Check if a location has been previously visited based on a list of visited coordinates
     */
    fun isLocationVisited(
        currentLat: Double, 
        currentLon: Double, 
        visitedLocations: List<Pair<Double, Double>>, 
        proximityThreshold: Double
    ): Boolean {
        return visitedLocations.any { (lat, lon) -> 
            areLocationsClose(currentLat, currentLon, lat, lon, proximityThreshold)
        }
    }
    
    /**
     * Generate a spiral search pattern around a center point
     * @param centerLat Center latitude
     * @param centerLon Center longitude
     * @param numPoints Number of points in the spiral
     * @param startRadius Starting radius in meters
     * @param radiusIncrement How much to increase radius per step
     * @return List of latitude,longitude pairs forming a spiral
     */
    fun generateSpiralPattern(
        centerLat: Double, 
        centerLon: Double, 
        numPoints: Int,
        startRadius: Double = 3.0,
        radiusIncrement: Double = 3.0
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var radius = startRadius
        
        // Add center point
        points.add(Pair(centerLat, centerLon))
        
        // Generate spiral points
        for (i in 1 until numPoints) {
            val angle = i * (2.0 * Math.PI / 8)  // Divide circle into 8 segments for smoother spiral
            radius += radiusIncrement / 8
            
            val bearing = Math.toDegrees(angle)
            val (lat, lon) = calculateCoordinates(centerLat, centerLon, radius, bearing)
            
            points.add(Pair(lat, lon))
        }
        
        return points
    }
}
