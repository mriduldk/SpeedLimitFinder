package com.jypko.speedlimitfinder

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.jypko.speedlimitfinder.adapter.DonatorAdapter
import com.jypko.speedlimitfinder.adapter.SpeedLimitAdapter
import com.jypko.speedlimitfinder.databinding.ActivityMainBinding
import com.jypko.speedlimitfinder.model.SpeedLimitZone
import com.jypko.speedlimitfinder.services.LocationForegroundService
import com.jypko.speedlimitfinder.utils.Constants
import com.jypko.speedlimitfinder.utils.SharedPref
import com.jypko.speedlimitfinder.viewmodel.DonatorViewModel
import com.jypko.speedlimitfinder.viewmodel.SpeedLimitViewModel
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val REQUEST_NOTIFICATION_PERMISSION = 123
    private val REQUEST_LOCATION_PERMISSION = 124
    private var hasLocationPermission = false
    private lateinit var adapter: SpeedLimitAdapter
    private lateinit var adapterDonator: DonatorAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        permissionForPushNotification()
        permissionForLocation()
        onClickListener()
        createNotificationChannel()
        setRecyclerViewData()
        getSpeedLimitZonesFromLocalDatabase()
        //getDonatorsFromLocalDatabase()
        checkFirstTimeLaunch()
    }

    private fun checkFirstTimeLaunch() {

        var isNotFirstLaunch = SharedPref().getBooleanPref(this, Constants.is_not_first_launch)

        if (!isNotFirstLaunch) {
            showInstructionDialog()
            SharedPref().setBoolean(this, Constants.is_not_first_launch, true)
        }
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

        binding.textViewDonateButton.setOnClickListener {
            showCopyDialog()
        }

        binding.textViewAddZoneButton.setOnClickListener {

            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            if (androidId == Constants.ADMIN_ANDROID_ID) {
                showAddSpeedLimitZoneDialogForAdmin()
            }
            else {
                showAddSpeedLimitZoneDialog()
            }
        }

        binding.textViewJypko.setOnClickListener {

            val url = "https://jypko.com"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage("com.android.chrome")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                intent.setPackage(null)
                startActivity(intent)
            }

        }

        binding.imageviewInstruction.setOnClickListener {
            showInstructionDialog()
        }

    }

    private fun getSpeedLimitZonesFromLocalDatabase() {

        val viewModel = ViewModelProvider(this)[SpeedLimitViewModel::class.java]

        viewModel.allZones.observe(this) { zones ->

            var updated = false

            val locationRequest = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(locationRequest, null)
                    .addOnSuccessListener { location ->
                        location?.let { userLocation ->

                            viewModel.allZones.observe(this) { zones ->

                                val updatedZones = zones.map { zone ->
                                    zone.copy(distanceFromUserKm = calculateDistance(
                                        userLocation.latitude, userLocation.longitude,
                                        zone.latitude, zone.longitude
                                    ))
                                }.sortedBy { it.distanceFromUserKm }
                                    .take(5)

                                adapter.updateList(updatedZones)
                                updated = true
                            }
                        }
                    }
            }

            if (!updated){
                adapter.updateList(zones)
            }

        }

    }

    private fun getDonatorsFromLocalDatabase() {

        val viewModel = ViewModelProvider(this)[DonatorViewModel::class.java]

        viewModel.allDonators.observe(this) { donators ->

            adapterDonator.updateList(donators)

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
            binding.textViewStartSpeedFinderButton.text = "Stop Speed Limit Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_red)
        } else {
            binding.textViewStartSpeedFinderButton.text = "Start Speed Limit Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_blue)
        }
    }

    private fun updateButtonTextManual(status: String) {
        if (status == "START") {
            binding.textViewStartSpeedFinderButton.text = "Stop Speed Limit Finder"
            binding.textViewStartSpeedFinderButton.background = ContextCompat.getDrawable(this, R.drawable.bg_text_view_round_home_red)
        } else {
            binding.textViewStartSpeedFinderButton.text = "Start Speed Limit Finder"
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

        adapter = SpeedLimitAdapter(this, emptyList())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        adapterDonator = DonatorAdapter(emptyList())
        binding.recyclerViewDonate.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDonate.adapter = adapterDonator

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

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000.0 // convert meters to KM
    }

    private fun showCopyDialog() {

        val upiId = "mridul.das.9706@oksbi"

        val message = """
        You can support us by donating to the UPI ID below:
        
        $upiId

        Note: Your donation will be reflected in the app within the next 24 hours.
        """.trimIndent()


        AlertDialog.Builder(this)
            .setTitle("Support Us 🙏")
            .setMessage(message)
            .setPositiveButton("Copy UPI") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("UPI", upiId)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(this, "UPI ID copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAddSpeedLimitZoneDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Speed Limit Zone")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val locationInput = EditText(this).apply {
            hint = "Enter Location Name"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setBackgroundResource(R.drawable.edit_text_background)
            setPadding(16,16,16,16)
        }

        val latitudeInput = EditText(this).apply {
            hint = "Enter Latitude"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundResource(R.drawable.edit_text_background)
            setPadding(16,16,16,16)
        }

        val longitudeInput = EditText(this).apply {
            hint = "Enter Longitude"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundResource(R.drawable.edit_text_background)
            setPadding(16,16,16,16)
        }

        val speedInput = EditText(this).apply {
            hint = "Enter Speed Limit (km/h)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setBackgroundResource(R.drawable.edit_text_background)
            setPadding(16,16,16,16)
        }

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 16
        }
        longitudeInput.layoutParams = layoutParams
        latitudeInput.layoutParams = layoutParams
        speedInput.layoutParams = layoutParams

        layout.addView(locationInput)
        layout.addView(latitudeInput)
        layout.addView(longitudeInput)
        layout.addView(speedInput)

        builder.setView(layout)

        builder.setPositiveButton("Add") { dialog, _ ->
            val latitude = latitudeInput.text.toString().trim()
            val longitude = longitudeInput.text.toString().trim()
            val location = locationInput.text.toString().trim()
            val speed = speedInput.text.toString().trim()

            if (location.isNotEmpty() && latitude.isNotEmpty() && longitude.isNotEmpty() && speed.isNotEmpty()) {
                //val message = "🚧 New Speed Limit Zone 🚗\n\n📍Location: $location\n🔒Limit: $speed km/h"
                val message = "🚧 New Speed Limit Zone 🚗\n\n📍Location: $location\n🌐Latitude: $latitude\n🌐Longitude: $longitude\n🔒Limit: $speed km/h"
                sendToWhatsApp(message)
            } else {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builder.show()
    }

    private fun sendToWhatsApp(message: String) {
        val phoneNumber = Constants.FEEDBACK_WHATSAPP_NUMBER

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$phoneNumber?text=" + URLEncoder.encode(message, "UTF-8"))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddSpeedLimitZoneDialogForAdmin() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_input, null)

        val editLatitude = dialogView.findViewById<EditText>(R.id.editLatitude)
        val editLongitude = dialogView.findViewById<EditText>(R.id.editLongitude)
        val editSpeedLimit = dialogView.findViewById<EditText>(R.id.editSpeedLimit)
        val editSpeedLimitBuffer = dialogView.findViewById<EditText>(R.id.editSpeedLimitBuffer)
        val editLocationName = dialogView.findViewById<EditText>(R.id.editLocationName)
        val editLocationAddress = dialogView.findViewById<EditText>(R.id.editLocationAddress)

        AlertDialog.Builder(this)
            .setTitle("Enter Speed Limit Details")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val latitude = editLatitude.text.toString().toDoubleOrNull() ?: 0.0
                val longitude = editLongitude.text.toString().toDoubleOrNull() ?: 0.0
                val speedLimit = editSpeedLimit.text.toString().toFloatOrNull() ?: 40f
                val speedLimitBuffer = editSpeedLimitBuffer.text.toString().toFloatOrNull() ?: 43f
                val locationName = editLocationName.text.toString()
                val locationAddress = editLocationAddress.text.toString()

                val data = SpeedLimitZone(
                    latitude = latitude,
                    longitude = longitude,
                    speedLimit = speedLimit,
                    speedLimitBuffer = speedLimitBuffer,
                    locationName = locationName,
                    locationAddress = locationAddress,
                )

                Firebase.firestore.collection("speedLimitZones")
                    .add(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Location added successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                // Use the data as needed
                Toast.makeText(this, "Location added: $locationName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInstructionDialog() {
        val message = """
        🚀 How to Use Speed Limit Finder

        ✅ Click on "Start Speed Limit Finder" button.
        ✅ The app will detect nearby speed limit zones.
        ✅ You’ll get alerts 200 meters before a zone if you're speeding.

        📱 Once started, you'll receive a notification — feel free to minimize the app.

        🛑 To stop, click on "Stop Speed Limit Finder" button.

        Stay safe and drive responsibly! 🚗💨
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Instructions 📋")
            .setMessage(message)
            .setPositiveButton("Got It") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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