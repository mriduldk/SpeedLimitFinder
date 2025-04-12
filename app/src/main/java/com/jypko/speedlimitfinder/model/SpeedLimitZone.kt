package com.jypko.speedlimitfinder.model

data class SpeedLimitZone(
    val latitude: Double,
    val longitude: Double,
    val speedLimit: Float = 30f,
    val speedLimitBuffer: Float = 5f,
    val locationName: String = "",
    val locationAddress: String = "",
)
