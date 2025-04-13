package com.jypko.speedlimitfinder.localdatabase

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jypko.speedlimitfinder.model.Donator
import com.jypko.speedlimitfinder.model.SpeedLimitZone

@Dao
interface DonatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<Donator>)

    @Query("DELETE FROM donator")
    suspend fun deleteAll()

    @Query("SELECT * FROM donator")
    fun getAll(): LiveData<List<Donator>>

    @Query("SELECT * FROM donator LIMIT 5")
    fun getTop5(): List<Donator>

}