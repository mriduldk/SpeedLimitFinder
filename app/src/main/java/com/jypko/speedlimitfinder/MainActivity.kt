package com.jypko.speedlimitfinder

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.jypko.speedlimitfinder.adapter.SpeedLimitAdapter
import com.jypko.speedlimitfinder.databinding.ActivityMainBinding
import com.jypko.speedlimitfinder.model.SpeedLimitZone
import com.jypko.speedlimitfinder.services.LocationForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val REQUEST_NOTIFICATION_PERMISSION = 123
    private val REQUEST_LOCATION_PERMISSION = 124
    private var hasLocationPermission = false

    private val speedLimitZones = listOf(
        SpeedLimitZone(26.577515, 93.760719, 40F, 45F, "Goshanibar, Kazironga", "JG73+H6P, Assam Trunk Rd, Goshanibar, Assam 785612"),
        SpeedLimitZone(26.586206, 93.752060, 40F, 45F, "Kohora, Kazironga", "H3GH+JVV, Assam Trunk Rd, Amguri Sang, Assam 782136"),
        SpeedLimitZone(26.586206, 93.752060, 40F, 45F, "Bokakhat", "H8PV+J47, Assam Trunk Rd, Bagari N.C., Assam 785609")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        permissionForPushNotification()
        permissionForLocation()
        onClickListener()
        createNotificationChannel()
        setRecyclerViewData()
    }

    private fun permissionForPushNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun permissionForLocation() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && // Android 14 (API 34)
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        hasLocationPermission = if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_LOCATION_PERMISSION)
            false
        } else {
            true
        }
    }

    private fun onClickListener() {

        binding.textViewNotificationButton.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }

        }

        binding.textViewStartSpeedFinderButton.setOnClickListener {

            if (hasLocationPermission) {
                if (isMyServiceRunning(LocationForegroundService::class.java)) {
                    Log.d("isMyServiceRunning", "isMyServiceRunning: Yes")
                    stopLocationService()
                } else {
                    Log.d("isMyServiceRunning", "isMyServiceRunning: No")
                    startSpeedTrackingService()
                }
            }
            else {
                permissionForLocation()
            }
        }
    }

    private fun startSpeedTrackingService() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, LocationForegroundService::class.java))
        } else {
            startService(Intent(this, LocationForegroundService::class.java))
        }
        updateButtonTextManual("START")
    }

    private fun updateButtonText() {
        if (isMyServiceRunning(LocationForegroundService::class.java)) {
            binding.textViewStartSpeedFinderButton.text = "Stop Speed Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_red)
        } else {
            binding.textViewStartSpeedFinderButton.text = "Start Speed Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_blue)
        }
    }

    private fun updateButtonTextManual(status: String) {
        if (status == "START") {
            binding.textViewStartSpeedFinderButton.text = "Stop Speed Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_red)
        } else {
            binding.textViewStartSpeedFinderButton.text = "Start Speed Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_blue)
        }
    }

    private fun stopLocationService() {
        val stopIntent = Intent(this, LocationForegroundService::class.java)
        stopIntent.action = "STOP"
        stopService(stopIntent)
        updateButtonTextManual("STOP")
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun setRecyclerViewData() {

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = SpeedLimitAdapter(this, speedLimitZones)

    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "speed_channel",
                "Speed Tracker",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

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
        else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                hasLocationPermission = true
            } else {
                Toast.makeText(this, "All permissions are required for the app to function", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSnackBarMessage(message: String){
        Snackbar.make(binding.constraintLayoutParent, message, Snackbar.LENGTH_LONG).show()
    }

    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val speed = intent?.getIntExtra("speed_kmh", 0) ?: 0
            binding.textViewSpeed.text = "$speed km/h"

        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        checkNotificationPermission()
        updateButtonText()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(speedReceiver, IntentFilter("com.jypko.speedlimitfinder.SPEED_UPDATE"))

    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(speedReceiver)
    }


}