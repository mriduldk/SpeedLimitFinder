package com.jypko.speedlimitfinder.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "speed_limit_zones")
data class SpeedLimitZone(
    @PrimaryKey
    val latitude: Double,
    val longitude: Double,
    val speedLimit: Float = 30f,
    val speedLimitBuffer: Float = 5f,
    val locationName: String = "",
    val locationAddress: String = "",
    var distanceFromUserKm: Double = 0.0
)
