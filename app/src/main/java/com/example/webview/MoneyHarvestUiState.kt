package com.example.webview

enum class WebErrorType {
  OFFLINE,
  GENERIC,
}

data class MoneyHarvestUiState(
  val isOnline: Boolean = true,
  val canGoBack: Boolean = false,
  val errorType: WebErrorType? = null,
)
