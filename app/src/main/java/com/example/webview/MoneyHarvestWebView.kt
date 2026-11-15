package com.example.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MoneyHarvestScreen(
  uiState: MoneyHarvestUiState,
  savedBundle: Bundle?,
  onSaveBundle: (Bundle) -> Unit,
  onCanGoBackChanged: (Boolean) -> Unit,
  onMainFrameError: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  var customFullscreenView by remember { mutableStateOf<View?>(null) }
  var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
  var hasLoadedInitialUrl by remember { mutableStateOf(false) }

  val filePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val callback = pendingFileCallback
      pendingFileCallback = null
      if (callback != null) {
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
      }
    }

  BackHandler(enabled = customFullscreenView != null || uiState.canGoBack) {
    if (customFullscreenView != null) {
      customViewCallback?.onCustomViewHidden()
      customFullscreenView = null
      customViewCallback = null
    } else {
      val webView = webViewRef
      if (webView != null && webView.canGoBack()) {
        webView.goBack()
      }
    }
  }

  // Automatically reload when connectivity returns after an offline error
  LaunchedEffect(uiState.isOnline, uiState.errorType) {
    if (uiState.isOnline && uiState.errorType == WebErrorType.OFFLINE) {
      val webView = webViewRef
      if (webView != null && hasLoadedInitialUrl) {
        onRetry()
        val currentUrl = webView.url
        if (currentUrl.isNullOrBlank() || currentUrl == "about:blank") {
          webView.loadUrl(WebViewPolicy.TARGET_URL)
        } else {
          webView.reload()
        }
      }
    }
  }

  DisposableEffect(lifecycleOwner, webViewRef) {
    val observer = LifecycleEventObserver { _, event ->
      val webView = webViewRef ?: return@LifecycleEventObserver
      when (event) {
        Lifecycle.Event.ON_RESUME -> webView.onResume()
        Lifecycle.Event.ON_PAUSE -> {
          val bundle = Bundle()
          webView.saveState(bundle)
          onSaveBundle(bundle)
          CookieManager.getInstance().flush()
          webView.onPause()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      pendingFileCallback?.onReceiveValue(null)
      pendingFileCallback = null
    }
  }

  Surface(
    modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        modifier = Modifier.fillMaxSize().testTag("money_harvest_webview"),
        factory = { viewContext ->
          WebView(viewContext).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            isFocusableInTouchMode = true

            val webViewInstance = this
            CookieManager.getInstance().apply {
              setAcceptCookie(true)
              setAcceptThirdPartyCookies(webViewInstance, true)
            }

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              useWideViewPort = true
              loadWithOverviewMode = true
              setSupportZoom(true)
              builtInZoomControls = true
              displayZoomControls = false
              mediaPlaybackRequiresUserGesture = false
              allowFileAccess = false
              allowContentAccess = true
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
              cacheMode = WebSettings.LOAD_DEFAULT
              javaScriptCanOpenWindowsAutomatically = true
              setSupportMultipleWindows(true)
            }

            setDownloadListener(
              DownloadListener { url, _, _, _, _ ->
                if (!url.isNullOrBlank()) {
                  WebViewPolicy.openExternalUri(viewContext, Uri.parse(url))
                }
              }
            )

            webViewClient =
              object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?,
                ): Boolean {
                  val uri = request?.url ?: return false
                  return when (val action = WebViewPolicy.resolveNavigation(uri)) {
                    WebViewPolicy.NavigationAction.LoadInWebView -> false
                    is WebViewPolicy.NavigationAction.OpenExternalBrowser -> {
                      WebViewPolicy.openExternalUri(viewContext, action.uri)
                      true
                    }
                    is WebViewPolicy.NavigationAction.OpenExternalIntent -> {
                      WebViewPolicy.openExternalUri(viewContext, action.uri)
                      true
                    }
                    WebViewPolicy.NavigationAction.Ignore -> true
                  }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  onCanGoBackChanged(view?.canGoBack() == true)
                }

                override fun doUpdateVisitedHistory(
                  view: WebView?,
                  url: String?,
                  isReload: Boolean,
                ) {
                  super.doUpdateVisitedHistory(view, url, isReload)
                  onCanGoBackChanged(view?.canGoBack() == true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  onCanGoBackChanged(view?.canGoBack() == true)
                }

                override fun onReceivedError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  error: WebResourceError?,
                ) {
                  super.onReceivedError(view, request, error)
                  val isMainFrame = request?.isForMainFrame == true
                  val code = error?.errorCode ?: ERROR_UNKNOWN
                  if (WebViewPolicy.shouldShowErrorPage(code, isMainFrame)) {
                    onMainFrameError()
                  }
                }

                override fun onRenderProcessGone(
                  view: WebView?,
                  detail: android.webkit.RenderProcessGoneDetail?,
                ): Boolean {
                  onMainFrameError()
                  return true
                }
              }

            webChromeClient =
              object : WebChromeClient() {
                override fun onShowFileChooser(
                  webView: WebView?,
                  filePathCallback: ValueCallback<Array<Uri>>?,
                  fileChooserParams: FileChooserParams?,
                ): Boolean {
                  pendingFileCallback?.onReceiveValue(null)
                  pendingFileCallback = filePathCallback

                  val intent =
                    try {
                      fileChooserParams?.createIntent()
                    } catch (_: Exception) {
                      null
                    }
                      ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                      }

                  return try {
                    filePickerLauncher.launch(intent)
                    true
                  } catch (_: Exception) {
                    pendingFileCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                  }
                }

                override fun onCreateWindow(
                  view: WebView?,
                  isDialog: Boolean,
                  isUserGesture: Boolean,
                  resultMsg: Message?,
                ): Boolean {
                  val mainWebView = view ?: return false
                  val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                  val tempWebView =
                    WebView(viewContext).apply {
                      webViewClient =
                        object : WebViewClient() {
                          override fun shouldOverrideUrlLoading(
                            popupView: WebView?,
                            request: WebResourceRequest?,
                          ): Boolean {
                            val targetUri = request?.url ?: return true
                            when (val action = WebViewPolicy.resolveNavigation(targetUri)) {
                              WebViewPolicy.NavigationAction.LoadInWebView -> {
                                mainWebView.loadUrl(targetUri.toString())
                              }
                              is WebViewPolicy.NavigationAction.OpenExternalBrowser -> {
                                WebViewPolicy.openExternalUri(viewContext, action.uri)
                              }
                              is WebViewPolicy.NavigationAction.OpenExternalIntent -> {
                                WebViewPolicy.openExternalUri(viewContext, action.uri)
                              }
                              WebViewPolicy.NavigationAction.Ignore -> Unit
                            }
                            popupView?.destroy()
                            return true
                          }
                        }
                    }
                  transport.webView = tempWebView
                  resultMsg.sendToTarget()
                  return true
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                  if (customFullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                  }
                  customFullscreenView = view
                  customViewCallback = callback
                }

                override fun onHideCustomView() {
                  customFullscreenView = null
                  customViewCallback?.onCustomViewHidden()
                  customViewCallback = null
                }
              }

            if (savedBundle != null && !savedBundle.isEmpty) {
              restoreState(savedBundle)
            } else {
              loadUrl(WebViewPolicy.TARGET_URL)
            }
            hasLoadedInitialUrl = true
            webViewRef = this
          }
        },
        update = { webView ->
          webViewRef = webView
        },
      )

      customFullscreenView?.let { fsView ->
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            FrameLayout(ctx).apply {
              (fsView.parent as? ViewGroup)?.removeView(fsView)
              addView(
                fsView,
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                ),
              )
            }
          },
        )
      }

      if (uiState.errorType != null) {
        WebErrorOverlay(
          errorType = uiState.errorType,
          onRetry = {
            onRetry()
            val webView = webViewRef
            if (webView != null) {
              val currentUrl = webView.url
              if (currentUrl.isNullOrBlank() || currentUrl == "about:blank") {
                webView.loadUrl(WebViewPolicy.TARGET_URL)
              } else {
                webView.reload()
              }
            }
          },
          onOpenSettings = {
            try {
              context.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              )
            } catch (_: Exception) {
              // Ignore if settings activity unavailable
            }
          },
        )
      }
    }
  }
}

@Composable
private fun WebErrorOverlay(
  errorType: WebErrorType,
  onRetry: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxSize().testTag("error_overlay"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector =
            if (errorType == WebErrorType.OFFLINE) {
              Icons.Rounded.CloudOff
            } else {
              Icons.Rounded.ErrorOutline
            },
          contentDescription = stringResource(R.string.error_title),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.error_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            if (errorType == WebErrorType.OFFLINE) {
              stringResource(R.string.error_offline_message)
            } else {
              stringResource(R.string.error_generic_message)
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.action_retry))
          }
          if (errorType == WebErrorType.OFFLINE) {
            OutlinedButton(
              onClick = onOpenSettings,
              modifier = Modifier.testTag("network_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(8.dp))
              Text(text = stringResource(R.string.action_open_settings))
            }
          }
        }
      }
    }
  }
}
