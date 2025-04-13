package com.jypko.speedlimitfinder.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPref {

    private lateinit var sharedpreferences: SharedPreferences

    fun setString(context: Context, key: String?, value: String?) {

        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        val editor = sharedpreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getStringPref(context: Context, key: String?): String? {
        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        return sharedpreferences.getString(key, "")
    }

    fun setLong(context: Context, key: String?, value: Long) {

        sharedpreferences = context.getSharedPreferences(Constants.MyPREFERENCES, Context.MODE_PRIVATE)
        val editor = sharedpreferences.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun setInt(context: Context, key: String?, value: Int) {

        sharedpreferences = context.getSharedPreferences(Constants.MyPREFERENCES, Context.MODE_PRIVATE)
        val editor = sharedpreferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }
    
    fun setUserID(context: Context, value: String?) {

        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        val editor = sharedpreferences.edit()
        editor.putString(Constants.user_id, value)
        editor.apply()
    }

    fun getUserIDPref(context: Context): String? {
        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        return sharedpreferences.getString(Constants.user_id, "")
    }

    fun setBoolean(context: Context, key: String?, value: Boolean) {

        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        val editor = sharedpreferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBooleanPref(context: Context, key: String?): Boolean {
        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        return sharedpreferences.getBoolean(key, false)
    }

    fun getLongPref(context: Context, key: String?): Long {
        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        return sharedpreferences.getLong(key, 0)
    }

    fun getIntPref(context: Context, key: String?): Int {
        sharedpreferences = context.getSharedPreferences(
            Constants.MyPREFERENCES,
            Context.MODE_PRIVATE
        )
        return sharedpreferences.getInt(key, 0)
    }



    fun logoutUser(context: Context) {
        sharedpreferences = context.getSharedPreferences(Constants.MyPREFERENCES, Context.MODE_PRIVATE)

        val editor = sharedpreferences.edit()
        editor.clear()
        editor.apply()
    }



}