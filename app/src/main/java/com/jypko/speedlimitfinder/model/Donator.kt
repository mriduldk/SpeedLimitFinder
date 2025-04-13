package com.jypko.speedlimitfinder.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "donator")
data class Donator(
    @PrimaryKey
    val donatorId: String,
    val name: String,
    val amount: Float,
    val address: String,
    val timeString: String,
    val time: Long
)
