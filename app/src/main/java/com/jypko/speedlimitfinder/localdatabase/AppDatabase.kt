package com.jypko.speedlimitfinder.localdatabase

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jypko.speedlimitfinder.model.SpeedLimitZone
import com.jypko.speedlimitfinder.utils.Constants

@Database(
    entities = [SpeedLimitZone::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun speedLimitZoneDao(): SpeedLimitZoneDao

//    companion object {
//
//        @Volatile
//        private var instance : AppDatabase?= null
//        private var LOCK = Any()
//
//        operator fun invoke(context: Context) = instance ?: synchronized(LOCK) {
//            instance ?: createDatabase(context).also { instance = it }
//        }
//
//        private fun createDatabase(context: Context) = Room.databaseBuilder(
//            context.applicationContext,
//            AppDatabase::class.java,
//            Constants.DATABASE_NAME
//        )
//            .fallbackToDestructiveMigration()
//            .build()
//
//    }


}