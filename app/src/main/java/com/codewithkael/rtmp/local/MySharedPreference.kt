package com.codewithkael.rtmp.local

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

class MySharedPreference @Inject constructor(
    context: Context,
) {
    private val pref: SharedPreferences = context.getSharedPreferences(
        "messenger",
        Context.MODE_PRIVATE
    )
    private val prefsEditor: SharedPreferences.Editor = pref.edit()

    fun setToken(token: String?) {
        prefsEditor.putString("token", token).apply()
    }

    fun getToken(): String? = pref.getString("token", null)


}