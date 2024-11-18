package net.izissise.hibreak_comp_service.quick_settings

import android.content.SharedPreferences

fun SharedPreferences.toggle(key: String) =
    edit().putBoolean(key, !getBoolean(key, false)).apply()
