package com.jypko.speedlimitfinder.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jypko.speedlimitfinder.R
import com.jypko.speedlimitfinder.localdatabase.AppDatabase
import com.jypko.speedlimitfinder.model.SpeedLimitZone
import com.jypko.speedlimitfinder.utils.Constants
import com.jypko.speedlimitfinder.viewmodel.SpeedLimitViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    //    private val speedLimitZones = listOf(
//        SpeedLimitZone(26.577515, 93.760719), // Example points
//        SpeedLimitZone(26.586206, 93.752060)
//    )
    private var speedLimitZones : List<SpeedLimitZone> ?= null

    private var isTracking = false
    private var job: Job? = null
    private lateinit var textToSpeech: TextToSpeech
    private val SLEEP_TIME = 1 * 15 * 1000L //1 * 60 * 1000L // 1 MINUTE
    private val TRACKING_DISMISS_DISTANCE = 20 // IN METER
    private val ALERT_DISTANCE = 300 // IN METER
    private val GET_NEAREST_ZONE_WITH_IN_5KM = 2 * 1000 // IN METER

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Init TTS
        textToSpeech = TextToSpeech(applicationContext) {
            it.takeIf { it == TextToSpeech.SUCCESS }?.let {
                textToSpeech.language = Locale.US
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundService()
        startCheckingNearbyZones()
        getSpeedLimitZonesFromLocalDatabase()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        //startForegroundService()

        return START_STICKY
    }

    private fun getSpeedLimitZonesFromLocalDatabase() {

        val localDB = Room.databaseBuilder(application, AppDatabase::class.java, Constants.DATABASE_NAME).fallbackToDestructiveMigration().build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allZones = localDB.speedLimitZoneDao().getAllForService()
                speedLimitZones = allZones
            } catch (e: Exception) {
                Log.e("ForegroundService", "Error fetching data from database: $e")
            }
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "speed_channel")
            .setContentTitle("Speed Tracker")
            .setContentText("Monitoring nearby speed zones")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        startForeground(1, notification)
    }

    private fun startCheckingNearbyZones() {

        var previousLocation: Location? = null

        job = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (!isTracking) {
                    val currentLocation = getLastKnownLocation() ?: continue
                    // Skip if we don't have previousLocation yet
                    if (previousLocation == null) {
                        previousLocation = currentLocation
                        delay(SLEEP_TIME)
                        continue
                    }
                    val nearestZone = getNearestZoneWithin5km(currentLocation)

                    if (nearestZone != null && isUserHeadingTowards(currentLocation, previousLocation, nearestZone)) {
                        isTracking = true
                        Log.d("isTracking", "isTracking: true")
                        trackSpeedUntilZoneCrossed(currentLocation, nearestZone)
                    } else {
                        Log.d("isTracking", "isTracking: false")
                        delay(SLEEP_TIME) // Sleep for 5 minutes
                    }

                    // Update previous location after processing
                    previousLocation = currentLocation
                }
            }
        }
    }

    private suspend fun trackSpeedUntilZoneCrossed(startLocation: Location, zone: SpeedLimitZone) {
        val zoneLocation = Location("").apply {
            latitude = zone.latitude
            longitude = zone.longitude
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                val distance = location.distanceTo(zoneLocation)
                val speedKmH = (location.speed * 3.6).roundToInt()

                Log.d("SpeedTrack", "Speed: $speedKmH km/h, Distance: $distance m")

                val intent = Intent("com.jypko.speedlimitfinder.SPEED_UPDATE")
                intent.putExtra("speed_kmh", speedKmH)
                LocalBroadcastManager.getInstance(this@LocationForegroundService).sendBroadcast(intent)


                if (distance < TRACKING_DISMISS_DISTANCE) {
                    fusedLocationClient.removeLocationUpdates(this)
                    isTracking = false
                    Log.d("SpeedTrack", "Zone crossed.")
                } else if (speedKmH > zone.speedLimitBuffer && distance < ALERT_DISTANCE ) {
                    // Speed warning (e.g., TTS or notification)
                    Log.w("SpeedTrack", "Speed exceeds limit: $speedKmH > ${zone.speedLimitBuffer} : Actual Speed Limit: ${zone.speedLimit}")

                    startRepeatingAlert(zone.speedLimit.toString())

                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun getNearestZoneWithin5km(current: Location): SpeedLimitZone? {
        return speedLimitZones?.minByOrNull {
            val loc = Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            current.distanceTo(loc)
        }?.takeIf {
            val loc = Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            current.distanceTo(loc) <= GET_NEAREST_ZONE_WITH_IN_5KM
        }
    }

    private fun isUserHeadingTowards(current: Location, zone: SpeedLimitZone): Boolean {
        val targetLoc = Location("").apply {
            latitude = zone.latitude
            longitude = zone.longitude
        }
        val bearingToTarget = current.bearingTo(targetLoc)
        val headingDiff = abs(bearingToTarget - current.bearing)
        return headingDiff < 90 // heading towards
    }
    private fun isUserHeadingTowards(
        current: Location,
        previous: Location?,
        zone: SpeedLimitZone
    ): Boolean {
        val targetLoc = Location("").apply {
            latitude = zone.latitude
            longitude = zone.longitude
        }

        // Compute bearings
        val bearingToTarget = current.bearingTo(targetLoc)
        val headingDiff = abs(bearingToTarget - current.bearing).let {
            if (it > 180) 360 - it else it
        }

        // Check if user is heading generally towards the target
        val isHeadingCorrect = headingDiff < 90

        // Check if the distance to the target is decreasing
        val prevDistance = previous?.distanceTo(targetLoc)
        val currDistance = current.distanceTo(targetLoc)

        var isGettingCloser = true
        if (prevDistance != null){
            isGettingCloser = currDistance < prevDistance
        }

        return isHeadingCorrect && isGettingCloser
    }

    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            /*fusedLocationClient.lastLocation.addOnSuccessListener {
                cont.resume(it)
            }.addOnFailureListener {
                cont.resume(null)
            }*/

            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                cont.resume(location)
            }.addOnFailureListener {
                cont.resume(null)
            }

            cont.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

        }
        else {
            cont.resume(null)
        }
    }

    private fun startRepeatingAlert(speedLimit: String) {
        CoroutineScope(Dispatchers.Main).launch {
            textToSpeech.speak(
                "Speed Limit Ahead. Reduce Speed to $speedLimit Kilometers per hour",
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
