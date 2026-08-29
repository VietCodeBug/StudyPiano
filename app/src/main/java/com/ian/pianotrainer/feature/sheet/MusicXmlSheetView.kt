package com.ian.pianotrainer.feature.sheet

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MusicXmlSheetView(
    xmlContent: String,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef: WebView? = remember { null }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("music_xml_sheet_view")
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // Strictly isolate: block any non-asset/external internet requests
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (!url.startsWith("file:///android_asset/")) {
                                // Block external network calls entirely
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (xmlContent.isNotBlank()) {
                                val escapedXml = xmlContent
                                    .replace("\\", "\\\\")
                                    .replace("`", "\\`")
                                    .replace("$", "\\$")
                                view?.evaluateJavascript("loadMusicXml(`$escapedXml`);", null)
                            }
                        }
                    }
                    loadUrl("file:///android_asset/osmd/osmd_viewer.html")
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Sync playback position
        LaunchedEffect(currentPositionMs) {
            webViewRef?.evaluateJavascript("updateCursor($currentPositionMs);", null)
        }
    }
}
