package com.jypko.speedlimitfinder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.WorkManager

class StopSpeedMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WorkManager.getInstance(context).cancelAllWorkByTag("SpeedMonitor")

        Toast.makeText(context, "Speed monitoring stopped", Toast.LENGTH_SHORT).show()
    }
}
