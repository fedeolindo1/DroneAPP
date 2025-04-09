package com.scania.droneautofly.model

/**
 * Modelo que representa o status atual do drone
 */
data class DroneStatus(
    val batteryPercent: Float = 0f,
    val altitude: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isFlying: Boolean = false,
    val velocity: Float = 0f,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f
)