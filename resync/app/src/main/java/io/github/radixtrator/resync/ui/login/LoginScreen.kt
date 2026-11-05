@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.radixtrator.resync.AppContainer
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Reddit's OAuth page in a WebView. Reddit redirects to the configured redirect URI
 * (e.g. redreader://rr_oauth_redir?state=…&code=…); we catch that before the WebView tries to
 * load it, so no intent filter is needed and any scheme works.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(container: AppContainer, onFinished: () -> Unit) {
    val state = rememberSaveable { UUID.randomUUID().toString() }
    val authorizeUrl = remember { runCatching { container.client.authorizeUrl(state) } }
    val redirectUri = remember { runCatching { container.client.redirectUri }.getOrDefault("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(authorizeUrl.exceptionOrNull()?.message) }
    var handled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun handleRedirect(uri: Uri) {
        if (handled) return
        handled = true
        val code = uri.getQueryParameter("code")
        when {
            uri.getQueryParameter("state") != state -> error = "Login failed: state mismatch. Try again."
            uri.getQueryParameter("error") == "access_denied" -> onFinished()
            uri.getQueryParameter("error") != null -> error = "Reddit said: ${uri.getQueryParameter("error")}"
            code == null -> error = "Reddit didn't return a login code."
            else -> {
                busy = true
                scope.launch {
                    runCatching { container.login(code) }
                        .onSuccess { onFinished() }
                        .onFailure { busy = false; error = it.message ?: "Login failed" }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in to Reddit") },
                navigationIcon = { IconButton(onClick = onFinished) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val url = authorizeUrl.getOrNull()
            when {
                error != null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onFinished) { Text("Back") }
                }
                busy -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                url != null -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        CookieManager.getInstance().removeAllCookies(null)
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.url.toString().startsWith(redirectUri)) return false
                                    handleRedirect(request.url)
                                    return true
                                }

                                // Some redirects after a form POST skip shouldOverrideUrlLoading.
                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    if (url.startsWith(redirectUri)) {
                                        view.stopLoading()
                                        handleRedirect(Uri.parse(url))
                                    }
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}
