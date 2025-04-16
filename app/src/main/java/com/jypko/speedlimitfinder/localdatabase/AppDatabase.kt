package com.jypko.speedlimitfinder.localdatabase

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jypko.speedlimitfinder.model.Donator
import com.jypko.speedlimitfinder.model.SpeedLimitZone

@Database(
    entities = [
        SpeedLimitZone::class,
        Donator::class
   ],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun speedLimitZoneDao(): SpeedLimitZoneDao
    abstract fun donatorDao(): DonatorDao

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