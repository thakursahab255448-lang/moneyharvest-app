package com.example

import android.content.Context
import android.net.Uri
import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.webview.MoneyHarvestScreen
import com.example.webview.MoneyHarvestUiState
import com.example.webview.MoneyHarvestViewModel
import com.example.webview.WebErrorType
import com.example.webview.WebViewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `app name and package name match requirements`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Money Harvest", context.getString(R.string.app_name))
    assertEquals("com.moneyharvest.app", context.packageName)
  }

  @Test
  fun `internal moneyharvest URLs load inside WebView`() {
    val rootUri = Uri.parse("https://moneyharvest.space-z.ai/#/")
    val subRouteUri = Uri.parse("https://moneyharvest.space-z.ai/#/dashboard")
    val subdomainUri = Uri.parse("https://app.moneyharvest.space-z.ai/login")

    assertEquals(
      WebViewPolicy.NavigationAction.LoadInWebView,
      WebViewPolicy.resolveNavigation(rootUri),
    )
    assertEquals(
      WebViewPolicy.NavigationAction.LoadInWebView,
      WebViewPolicy.resolveNavigation(subRouteUri),
    )
    assertEquals(
      WebViewPolicy.NavigationAction.LoadInWebView,
      WebViewPolicy.resolveNavigation(subdomainUri),
    )
  }

  @Test
  fun `external URLs and custom schemes open outside WebView`() {
    val externalUri = Uri.parse("https://example.com/terms")
    val mailtoUri = Uri.parse("mailto:support@moneyharvest.space-z.ai")
    val telUri = Uri.parse("tel:+15551234567")

    assertEquals(
      WebViewPolicy.NavigationAction.OpenExternalBrowser(externalUri),
      WebViewPolicy.resolveNavigation(externalUri),
    )
    assertEquals(
      WebViewPolicy.NavigationAction.OpenExternalIntent(mailtoUri),
      WebViewPolicy.resolveNavigation(mailtoUri),
    )
    assertEquals(
      WebViewPolicy.NavigationAction.OpenExternalIntent(telUri),
      WebViewPolicy.resolveNavigation(telUri),
    )
  }

  @Test
  fun `error classification only triggers on main frame network errors`() {
    assertTrue(WebViewPolicy.shouldShowErrorPage(WebViewClient.ERROR_HOST_LOOKUP, isForMainFrame = true))
    assertTrue(WebViewPolicy.shouldShowErrorPage(WebViewClient.ERROR_TIMEOUT, isForMainFrame = true))
    assertFalse(WebViewPolicy.shouldShowErrorPage(WebViewClient.ERROR_HOST_LOOKUP, isForMainFrame = false))
    assertFalse(WebViewPolicy.shouldShowErrorPage(WebViewClient.ERROR_UNSUPPORTED_SCHEME, isForMainFrame = true))
    assertFalse(WebViewPolicy.shouldShowErrorPage(-1, isForMainFrame = true))
  }

  @Test
  fun `viewModel updates connectivity back stack and error states`() {
    val vm = MoneyHarvestViewModel()
    assertTrue(vm.uiState.value.isOnline)
    assertFalse(vm.uiState.value.canGoBack)
    assertNull(vm.uiState.value.errorType)

    vm.onCanGoBackChanged(true)
    assertTrue(vm.uiState.value.canGoBack)

    vm.onConnectivityChanged(false)
    vm.onMainFrameError(isOnline = false)
    assertEquals(WebErrorType.OFFLINE, vm.uiState.value.errorType)

    vm.clearError()
    assertNull(vm.uiState.value.errorType)
  }

  @Test
  fun `moneyHarvestScreen renders webview and handles offline retry`() {
    var retried = false
    composeTestRule.setContent {
      MyApplicationTheme {
        MoneyHarvestScreen(
          uiState = MoneyHarvestUiState(isOnline = false, canGoBack = false, errorType = WebErrorType.OFFLINE),
          savedBundle = null,
          onSaveBundle = {},
          onCanGoBackChanged = {},
          onMainFrameError = {},
          onRetry = { retried = true },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("money_harvest_webview").assertIsDisplayed()
    composeTestRule.onNodeWithTag("error_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithTag("network_settings_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed().performClick()
    assertTrue(retried)
  }
}
