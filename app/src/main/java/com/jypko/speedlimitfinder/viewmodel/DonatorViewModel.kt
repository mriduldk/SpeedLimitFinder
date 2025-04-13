package com.jypko.speedlimitfinder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.room.Room
import com.jypko.speedlimitfinder.localdatabase.AppDatabase
import com.jypko.speedlimitfinder.model.Donator
import com.jypko.speedlimitfinder.utils.Constants

class DonatorViewModel(application: Application) : AndroidViewModel(application) {

    val localDB = Room.databaseBuilder(application, AppDatabase::class.java, Constants.DATABASE_NAME).fallbackToDestructiveMigration().build()
    val dao = localDB.donatorDao()

    // Expose LiveData
    val allDonators: LiveData<List<Donator>> = dao.getAll()


}
