package com.jypko.speedlimitfinder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.jypko.speedlimitfinder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val REQUEST_NOTIFICATION_PERMISSION = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //getspeed();
        createNotificationChannel();
        startWorkerThread();
        permissionForPushNotification();
    }

    private fun permissionForPushNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }


    }

    private fun onClickListener() {

        binding.cardViewPushNotification.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }

        }

    }

    private fun getspeed() {

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            // Start getting location updates
        }
        else {
            ActivityCompat.requestPermissions(this, arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location = locationResult.lastLocation ?: return
                val speedMps = location.speed
                val speedKmph = speedMps * 3.6

                Log.d("Speed", "Speed: %.2f km/h".format(speedKmph))
                binding.textViewSpeed.text = "Speed: %.2f km/h".format(speedKmph)
            }
        }

        startLocationUpdates()

    }

    private fun startWorkerThread() {

        val request = OneTimeWorkRequestBuilder<SpeedTrackingWorker>()
            .addTag("SpeedMonitor")
            .build()
        WorkManager.getInstance(this).enqueue(request)

    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000 // update interval in ms
        ).build()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "speed_channel",
                "Speed Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted, show the card
                binding.cardViewPushNotification.visibility = View.VISIBLE
            } else {
                // Permission is granted, hide the card
                binding.cardViewPushNotification.visibility = View.GONE
            }
        } else {
            // On versions below Android 13, permission is granted by default
            binding.cardViewPushNotification.visibility = View.GONE
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, hide the card
                binding.cardViewPushNotification.visibility = View.GONE
            } else {
                // Permission denied, maybe show a message to the user
                showSnackBarMessage("Notification permission is required!")
                val intent = Intent().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                }
                startActivity(intent)
            }
        }
    }

    private fun showSnackBarMessage(message: String){
        Snackbar.make(binding.constraintLayoutParent, message, Snackbar.LENGTH_LONG).show()
    }


}