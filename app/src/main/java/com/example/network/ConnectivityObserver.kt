package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityObserver {
  val isOnline: StateFlow<Boolean>
  fun start()
  fun stop()
}

class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {

  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
  override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private var isRegistered = false

  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        _isOnline.value = true
      }

      override fun onLost(network: Network) {
        _isOnline.value = checkCurrentConnectivity()
      }

      override fun onUnavailable() {
        _isOnline.value = false
      }

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
      ) {
        val hasInternet =
          networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        _isOnline.value = hasInternet
      }
    }

  override fun start() {
    if (isRegistered) return
    _isOnline.value = checkCurrentConnectivity()
    try {
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
      isRegistered = true
    } catch (_: Exception) {
      _isOnline.value = checkCurrentConnectivity()
    }
  }

  override fun stop() {
    if (!isRegistered) return
    try {
      connectivityManager.unregisterNetworkCallback(networkCallback)
    } catch (_: Exception) {
      // Ignore if already unregistered
    } finally {
      isRegistered = false
    }
  }

  private fun checkCurrentConnectivity(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
