package com.jypko.speedlimitfinder.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jypko.speedlimitfinder.R
import com.jypko.speedlimitfinder.model.SpeedLimitZone

class SpeedLimitAdapter(private val context: Context, private var zones: List<SpeedLimitZone>) : RecyclerView.Adapter<SpeedLimitAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textLocation: TextView = view.findViewById(R.id.textLocation)
        val textAddress: TextView = view.findViewById(R.id.textAddress)
        val textDistance: TextView = view.findViewById(R.id.textDistance)
        val textSpeedLimit: TextView = view.findViewById(R.id.textSpeedLimit)
        val textSeeOnMap: TextView = view.findViewById(R.id.textSeeOnMap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_speed_limit_zone, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = zones.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val zone = zones[position]
        holder.textLocation.text = zone.locationName
        holder.textAddress.text = zone.locationAddress
        holder.textDistance.text = "%.2f km away".format(zone.distanceFromUserKm)
        holder.textSpeedLimit.text = "${zone.speedLimit.toInt()}"

        holder.textSeeOnMap.setOnClickListener {
            openMapWithZone(context, zone)
        }
    }

    fun openMapWithZone(context: Context, zone: SpeedLimitZone) {
        val uri = Uri.parse("geo:${zone.latitude},${zone.longitude}?q=${zone.latitude},${zone.longitude}(${zone.locationName})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        context.startActivity(intent)
    }

    fun updateList(newItems: List<SpeedLimitZone>) {
        zones = newItems
        notifyDataSetChanged()
    }

}