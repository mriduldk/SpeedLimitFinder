package com.jypko.speedlimitfinder.localdatabase

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jypko.speedlimitfinder.model.SpeedLimitZone

@Dao
interface SpeedLimitZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<SpeedLimitZone>)

    @Query("DELETE FROM speed_limit_zones")
    suspend fun deleteAll()

    @Query("SELECT * FROM speed_limit_zones")
    fun getAll(): LiveData<List<SpeedLimitZone>>

    @Query("SELECT * FROM speed_limit_zones")
    fun getAllForService(): List<SpeedLimitZone>

    @Query("SELECT * FROM speed_limit_zones LIMIT 5")
    suspend fun getTop5(): List<SpeedLimitZone>

}