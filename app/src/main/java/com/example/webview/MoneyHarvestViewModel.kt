package com.example.webview

import android.os.Bundle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MoneyHarvestViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MoneyHarvestUiState())
  val uiState: StateFlow<MoneyHarvestUiState> = _uiState.asStateFlow()

  var webViewBundle: Bundle? = null

  fun onConnectivityChanged(isOnline: Boolean) {
    _uiState.update { current ->
      if (current.isOnline == isOnline) current else current.copy(isOnline = isOnline)
    }
  }

  fun onCanGoBackChanged(canGoBack: Boolean) {
    _uiState.update { current ->
      if (current.canGoBack == canGoBack) current else current.copy(canGoBack = canGoBack)
    }
  }

  fun onMainFrameError(isOnline: Boolean) {
    _uiState.update { current ->
      current.copy(
        errorType = if (!isOnline) WebErrorType.OFFLINE else WebErrorType.GENERIC
      )
    }
  }

  fun clearError() {
    _uiState.update { current ->
      if (current.errorType == null) current else current.copy(errorType = null)
    }
  }
}
