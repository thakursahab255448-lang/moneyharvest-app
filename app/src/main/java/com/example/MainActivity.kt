package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.AndroidConnectivityObserver
import com.example.ui.theme.MyApplicationTheme
import com.example.webview.MoneyHarvestScreen
import com.example.webview.MoneyHarvestViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private val viewModel: MoneyHarvestViewModel by viewModels()
  private lateinit var connectivityObserver: AndroidConnectivityObserver

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    connectivityObserver = AndroidConnectivityObserver(applicationContext)
    connectivityObserver.start()

    lifecycleScope.launch {
      connectivityObserver.isOnline.collectLatest { isOnline ->
        viewModel.onConnectivityChanged(isOnline)
      }
    }

    setContent {
      MyApplicationTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        MoneyHarvestScreen(
          uiState = uiState,
          savedBundle = viewModel.webViewBundle,
          onSaveBundle = { bundle -> viewModel.webViewBundle = bundle },
          onCanGoBackChanged = viewModel::onCanGoBackChanged,
          onMainFrameError = {
            viewModel.onMainFrameError(connectivityObserver.isOnline.value)
          },
          onRetry = viewModel::clearError,
        )
      }
    }
  }

  override fun onDestroy() {
    connectivityObserver.stop()
    super.onDestroy()
  }
}
