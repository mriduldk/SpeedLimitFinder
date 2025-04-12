package com.jypko.speedlimitfinder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.lang.Math.abs
import java.util.Locale

class SpeedTrackingWorker(
    ctx: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(ctx, workerParams) {

    /*private val speedLimitLatLng = LatLng(26.574872, 93.781102) // Replace with real target
    private val speedLimitLatLng2 = LatLng(26.577515, 93.760719) // Replace with real target
    private val speedLimitLatLng3 = LatLng(26.586206, 93.752060) // Replace with real target*/
    private val speedLimitZones = listOf(
        LatLng(26.577515, 93.760719),
        LatLng(26.586206, 93.752060)
    )

    private val SPEED_LIMIT = 40
    private val SPEED_LIMIT_BUFFER = SPEED_LIMIT + 5 // IN KM/H
    private val ALERT_DISTANCE = 100 /// IN METER
    private val NEAREST_DISTANCE = 100 /// IN METER
    private val LOCATION_REQUEST_INTERVAL = 1000L /// IN MILLISECOND



    private var alertRepeating = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var textToSpeech: TextToSpeech

    override suspend fun doWork(): Result {
        // Init TTS
        textToSpeech = TextToSpeech(applicationContext) {
            it.takeIf { it == TextToSpeech.SUCCESS }?.let {
                textToSpeech.language = Locale.US
            }
        }

        // Set Foreground Notification
        setForeground(createForegroundInfo("Speed monitoring active"))

        // Init location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L // 1 second interval
        ).build()

        val locationFlow = callbackFlow {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    trySend(result.lastLocation!!)
                }
            }

            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
            }
            /*fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            awaitClose { fusedLocationClient.removeLocationUpdates(callback) }*/
        }

        // Collect location updates
        locationFlow.collect { location ->
            handleLocationUpdate(location)
        }

        return Result.success()
    }

    private suspend fun handleLocationUpdate(location: Location) {

        /*val notificationId = 1
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isStopped) {
            notificationManager.cancel(notificationId)
        }*/

        val userLatLng = LatLng(location.latitude, location.longitude)
        val speedKmph = location.speed * 3.6f

        var nearestTarget: LatLng? = null
        var nearestDistance = Float.MAX_VALUE

        for (target in speedLimitZones) {
            val distance = distanceBetween(userLatLng, target)
            if (distance < nearestDistance) {
                nearestTarget = target
                nearestDistance = distance
            }
        }
        if (nearestTarget != null && nearestDistance <= NEAREST_DISTANCE) {
            val bearingToTarget = location.bearingTo(Location("").apply {
                latitude = nearestTarget.latitude
                longitude = nearestTarget.longitude
            })

            val isApproaching = isApproachingTarget(location.bearing, bearingToTarget)

            if (isApproaching && speedKmph > SPEED_LIMIT_BUFFER) {
                if (!alertRepeating) {
                    alertRepeating = true
                    startRepeatingAlert()
                }
            } else {
                alertRepeating = false
            }
        } else {
            alertRepeating = false
        }


        /*val distance = distanceBetween(userLatLng, speedLimitLatLng)

        val bearingToLimit = location.bearingTo(Location("").apply {
            latitude = speedLimitLatLng.latitude
            longitude = speedLimitLatLng.longitude
        })

        val isApproaching = isApproachingTarget(location.bearing, bearingToLimit)

        if (distance <= 200 && isApproaching && speedKmph > 45) {
            if (!alertRepeating) {
                alertRepeating = true
                startRepeatingAlert()
            }
        } else {
            alertRepeating = false
        }*/
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, result)
        return result[0]
    }

    private fun isApproachingTarget(currentBearing: Float, bearingToTarget: Float): Boolean {
        val angleDiff = abs(currentBearing - bearingToTarget)
        return angleDiff < 90 || angleDiff > 270
    }

    private fun startRepeatingAlert() {
        CoroutineScope(Dispatchers.Main).launch {
            while (alertRepeating) {
                textToSpeech.speak(
                    "Speed Limit Ahead. Reduce Speed to $SPEED_LIMIT Kilometers per hour",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
                delay(5000)
            }
        }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val channelId = "speed_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Speed Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(applicationContext, StopSpeedMonitorReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Speed Tracker")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            //.addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent) // 👈 Stop button
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            ForegroundInfo(1, notification)
        }
    }



}