package com.jypko.speedlimitfinder

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.firestore.FirebaseFirestore
import com.jypko.speedlimitfinder.localdatabase.AppDatabase
import com.jypko.speedlimitfinder.model.Donator
import com.jypko.speedlimitfinder.model.SpeedLimitZone
import com.jypko.speedlimitfinder.utils.Constants
import com.jypko.speedlimitfinder.utils.SharedPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SplashScreenActivity : AppCompatActivity() {

    private val TAG = "SplashScreenActivity"

    private lateinit var appUpdateManager : AppUpdateManager
    private val UPDATE_REQUEST_CODE = 3120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        appUpdateManager = AppUpdateManagerFactory.create(this@SplashScreenActivity)

        fetchFireStoreData()
        runOncePerDay()
    }

    private fun runOncePerDay() {

        val lastRunDate = SharedPref().getStringPref(this, Constants.last_run_date)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        Log.e(TAG, "runOncePerDay : $lastRunDate : $today" )

        if (lastRunDate != today) {

            appUpdateCheck()
            //fetchFireStoreData()
            //fetchFireStoreData()

            SharedPref().setString(this, Constants.last_run_date, today)
        }
        else {
            redirectToMainActivity()
        }
    }


    private fun appUpdateCheck() {

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        CoroutineScope(Dispatchers.Main).launch {

            delay(1000)
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->

                if(appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                    startTheUpdate(appUpdateInfo)
                    SharedPref().setBoolean(this@SplashScreenActivity, Constants.UPDATE_AVAILABLE, true)

                } else {
                    Log.e(TAG, "onCreate: Update not Available" )
                    SharedPref().setBoolean(this@SplashScreenActivity, Constants.UPDATE_AVAILABLE, false)
                    redirectToMainActivity()
                }

            }.addOnFailureListener { exception ->

                Log.e(TAG, "onCreate: Update Check Availability Failed:  $exception" )
                redirectToMainActivity()
            }
        }

    }

    private fun startTheUpdate(appUpdateInfo : AppUpdateInfo) {

        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, UPDATE_REQUEST_CODE)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UPDATE_REQUEST_CODE){

            if (resultCode != RESULT_OK){

                Log.e(TAG, "Update flow failed! Result code: $resultCode" )

                val appUpdateInfoTask = appUpdateManager.appUpdateInfo

                CoroutineScope(Dispatchers.Main).launch {

                    appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->

                        if(appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                            startTheUpdate(appUpdateInfo)
                            SharedPref().setBoolean(this@SplashScreenActivity, Constants.UPDATE_AVAILABLE, true)

                        } else {
                            Log.e(TAG, "onCreate: Update not Available" )
                            SharedPref().setBoolean(this@SplashScreenActivity, Constants.UPDATE_AVAILABLE, false)
                            redirectToMainActivity()
                        }

                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "onCreate: Update Check Availability Failed:  $exception" )
                        redirectToMainActivity()
                    }
                }

            }else {
                redirectToMainActivity()
            }
        }

    }

    private fun redirectToMainActivity() {

        startActivity(Intent(this, MainActivity::class.java))
        finish()

    }

    override fun onResume() {
        super.onResume()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->

            if(appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS){

                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    UPDATE_REQUEST_CODE
                )
            }
        }
    }

    private fun fetchFireStoreData() {

        val localDB = Room.databaseBuilder(
            this,
            AppDatabase::class.java, Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
        val dao = localDB.speedLimitZoneDao()

        val fireStoreDB = FirebaseFirestore.getInstance()

        fireStoreDB.collection("speedLimitZones")
            .get()
            .addOnSuccessListener { result ->

                val zones = result.map { doc ->
                    SpeedLimitZone(
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        speedLimit = (doc.getDouble("speedLimit") ?: 40.0).toFloat(),
                        speedLimitBuffer = (doc.getDouble("speedLimitBuffer") ?: 43.0).toFloat(),
                        locationName = doc.getString("locationName") ?: "",
                        locationAddress = doc.getString("locationAddress") ?: ""
                    )
                }

                CoroutineScope(Dispatchers.IO).launch {
                    dao.deleteAll()
                    dao.insertAll(zones)
                }

            }
            .addOnFailureListener { exception ->
                Log.w("FireStoreData", "Error getting documents: ", exception)
            }

    }

    private fun fetchDonatorFireStoreData() {

        val localDB = Room.databaseBuilder(
            this,
            AppDatabase::class.java, Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
        val dao = localDB.donatorDao()

        val fireStoreDB = FirebaseFirestore.getInstance()

        fireStoreDB.collection("donator")
            .get()
            .addOnSuccessListener { result ->

                val donators = result.map { doc ->
                    Donator(
                        donatorId = doc.getString("donatorId") ?: "",
                        name = doc.getString("name") ?: "",
                        amount = (doc.getDouble("amount") ?: 0.0).toFloat(),
                        address = doc.getString("address") ?: "",
                        timeString = doc.getString("timeString") ?: "",
                        time = doc.getLong("time") ?: 0
                    )
                }

                CoroutineScope(Dispatchers.IO).launch {
                    dao.deleteAll()
                    dao.insertAll(donators)
                }

            }
            .addOnFailureListener { exception ->
                Log.w("FireStoreData", "Error getting documents: ", exception)
            }

    }

}