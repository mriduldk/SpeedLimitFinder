package com.jypko.speedlimitfinder.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jypko.speedlimitfinder.R
import com.jypko.speedlimitfinder.model.Donator

class DonatorAdapter(private var donators: List<Donator>) : RecyclerView.Adapter<DonatorAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textName)
        val textAddress: TextView = view.findViewById(R.id.textAddress)
        val textAmount: TextView = view.findViewById(R.id.textAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donator, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = donators.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val donator = donators[position]
        holder.textName.text = donator.name
        holder.textAddress.text = donator.address
        holder.textAmount.text = "₹${donator.amount}"

    }

    fun updateList(newItems: List<Donator>) {
        donators = newItems
        notifyDataSetChanged()
    }

}