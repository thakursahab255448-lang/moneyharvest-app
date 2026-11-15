package com.example.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebViewClient

object WebViewPolicy {
  const val TARGET_URL = "https://moneyharvest.space-z.ai/#/"
  const val ALLOWED_HOST = "moneyharvest.space-z.ai"

  sealed interface NavigationAction {
    data object LoadInWebView : NavigationAction
    data class OpenExternalBrowser(val uri: Uri) : NavigationAction
    data class OpenExternalIntent(val uri: Uri) : NavigationAction
    data object Ignore : NavigationAction
  }

  fun resolveNavigation(uri: Uri?): NavigationAction {
    if (uri == null) return NavigationAction.Ignore
    val scheme = uri.scheme?.lowercase() ?: return NavigationAction.Ignore

    return when (scheme) {
      "http",
      "https" -> {
        val host = uri.host?.lowercase() ?: return NavigationAction.Ignore
        if (host == ALLOWED_HOST || host.endsWith(".$ALLOWED_HOST")) {
          NavigationAction.LoadInWebView
        } else {
          NavigationAction.OpenExternalBrowser(uri)
        }
      }
      "about",
      "blob",
      "data",
      "javascript" -> NavigationAction.LoadInWebView
      else -> NavigationAction.OpenExternalIntent(uri)
    }
  }

  fun shouldShowErrorPage(errorCode: Int, isForMainFrame: Boolean): Boolean {
    if (!isForMainFrame) return false
    return when (errorCode) {
      WebViewClient.ERROR_UNSUPPORTED_SCHEME -> false
      -1 -> false // ERR_ABORTED / cancelled navigation
      else -> true
    }
  }

  fun openExternalUri(context: Context, uri: Uri): Boolean {
    return try {
      if (uri.scheme.equals("intent", ignoreCase = true)) {
        val parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        if (parsedIntent.resolveActivity(context.packageManager) != null) {
          context.startActivity(parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          true
        } else {
          val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
          if (!fallbackUrl.isNullOrBlank()) {
            val fallbackIntent =
              Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
            true
          } else {
            false
          }
        }
      } else {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
      }
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: Exception) {
      false
    }
  }
}
