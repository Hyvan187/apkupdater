package com.apkupdater.glass

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object AutomaticChecks {
    fun allowed(context: Context, enabled: Boolean, wifiOnly: Boolean): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        AutoCheckPolicy.networkAllowed(enabled, wifiOnly, caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true, manager.isActiveNetworkMetered)
    }.getOrDefault(false)
}
