package com.tutu.meowhub.feature.terminal

import android.annotation.SuppressLint
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.view.TerminalView
import com.tutu.meowhub.R
import com.tutu.meowhub.core.terminal.MeowTerminalViewClient

private val MeowGold = Color(0xFFF5B731)
private val MeowGoldDark = Color(0xFFD49A1A)
private val MeowGoldLight = Color(0xFFFDD663)
private val MeowBg = Color(0xFF1C1A16)
private val MeowSurface = Color(0xFF2A2518)
private val MeowCard = Color(0xFF362F24)
private val MeowText = Color(0xFFF5EDD8)
private val MeowTextDim = Color(0xFFB8AD9A)
private val MeowTermBg = Color(0xFF0A0E14)

private enum class TerminalTab { TERMINAL, CONSOLE }

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = viewModel()
) {
    val bootstrapState by viewModel.bootstrapState.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val updateTrigger by viewModel.terminalUpdateTrigger.collectAsState()

    val sessionList by viewModel.sessionList.collectAsState()
    val currentSessionIdx by viewModel.currentSessionIndex.collectAsState()
    val consoleUrl by viewModel.consoleUrl.collectAsState()

    var activeTab by remember { mutableStateOf(TerminalTab.TERMINAL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeowBg)
    ) {
        TerminalTopBar(
            sessionCount = viewModel.sessionCount.collectAsState().value,
            sessionList = sessionList,
            currentSessionIndex = currentSessionIdx,
            onNewSession = { viewModel.createNewSession() },
            onSwitchSession = { viewModel.switchSession(it) },
            activeTab = activeTab,
            onTabSwitch = { activeTab = it },
            consoleAvailable = consoleUrl != null
        )

        when (bootstrapState) {
            TerminalViewModel.BootstrapState.CHECKING -> {
                LoadingContent(stringResource(R.string.term_checking_env))
            }
            TerminalViewModel.BootstrapState.NOT_INSTALLED -> {
                InstallContent(
                    onInstall = { viewModel.installBootstrap() }
                )
            }
            TerminalViewModel.BootstrapState.INSTALLING -> {
                InstallingContent(progress = installProgress)
            }
            TerminalViewModel.BootstrapState.ERROR -> {
                ErrorContent(
                    message = errorMessage ?: stringResource(R.string.term_unknown_error),
                    onRetry = { viewModel.installBootstrap() }
                )
            }
            TerminalViewModel.BootstrapState.INSTALLED -> {
                when (activeTab) {
                    TerminalTab.TERMINAL -> {
                        if (currentSession != null) {
                            TerminalContent(
                                viewModel = viewModel,
                                updateTrigger = updateTrigger,
                                modifier = Modifier.weight(1f)
                            )
                            ExtraKeysBar(
                                onKey = { key ->
                                    val session = viewModel.currentSession.value ?: return@ExtraKeysBar
                                    when (key) {
                                        "ESC" -> session.write("\u001b")
                                        "TAB" -> session.write("\t")
                                        "CTRL" -> {}
                                        "ALT" -> {}
                                        "↑" -> session.write("\u001b[A")
                                        "↓" -> session.write("\u001b[B")
                                        "←" -> session.write("\u001b[D")
                                        "→" -> session.write("\u001b[C")
                                        "-" -> session.write("-")
                                        "/" -> session.write("/")
                                        "|" -> session.write("|")
                                        "~" -> session.write("~")
                                        "_" -> session.write("_")
                                    }
                                }
                            )
                        } else {
                            LoadingContent(stringResource(R.string.term_starting))
                        }
                    }
                    TerminalTab.CONSOLE -> {
                        if (consoleUrl != null) {
                            ConsoleWebView(
                                url = consoleUrl!!,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MeowGold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        stringResource(R.string.term_waiting_gateway),
                                        color = MeowTextDim,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.term_gateway_then_console),
                                        color = MeowTextDim.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalTopBar(
    sessionCount: Int,
    sessionList: List<Pair<Int, String>>,
    currentSessionIndex: Int,
    onNewSession: () -> Unit,
    onSwitchSession: (Int) -> Unit,
    activeTab: TerminalTab,
    onTabSwitch: (TerminalTab) -> Unit,
    consoleAvailable: Boolean
) {
    Surface(
        color = MeowSurface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🐱", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.term_product_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeowGold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (activeTab == TerminalTab.TERMINAL) {
                    IconButton(onClick = onNewSession, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New session",
                            tint = MeowGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabChip(
                    label = stringResource(R.string.term_tab_terminal),
                    isActive = activeTab == TerminalTab.TERMINAL,
                    onClick = { onTabSwitch(TerminalTab.TERMINAL) }
                )
                TabChip(
                    label = stringResource(R.string.term_tab_console),
                    isActive = activeTab == TerminalTab.CONSOLE,
                    onClick = { onTabSwitch(TerminalTab.CONSOLE) },
                    badge = consoleAvailable
                )
            }

            if (activeTab == TerminalTab.TERMINAL && sessionList.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sessionList.forEach { (index, label) ->
                        val isActive = index == currentSessionIndex
                        Surface(
                            modifier = Modifier
                                .height(30.dp)
                                .clickable { onSwitchSession(index) },
                            color = if (isActive) MeowGold.copy(alpha = 0.25f) else MeowCard,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (label == "Gateway") "⚡ Gateway" else "⌨ ${index + 1}",
                                    color = if (isActive) MeowGold else MeowTextDim,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        color = if (isActive) MeowGold.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = if (isActive) MeowGold else MeowTextDim,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            if (badge) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun TerminalContent(
    viewModel: TerminalViewModel,
    updateTrigger: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewClient = remember { MeowTerminalViewClient() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val session = viewModel.currentSession.collectAsState().value

    LaunchedEffect(updateTrigger) {
        terminalView?.onScreenUpdated()
    }

    LaunchedEffect(session) {
        if (session != null) {
            terminalView?.let { tv ->
                tv.attachSession(session)
                tv.onScreenUpdated()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val density = ctx.resources.displayMetrics.density
                val textSizePx = (11 * density + 0.5f).toInt()
                TerminalView(ctx, null).apply {
                    setTerminalViewClient(viewClient)
                    setTextSize(textSizePx)
                    setTypeface(android.graphics.Typeface.MONOSPACE)
                    setBackgroundColor(android.graphics.Color.parseColor("#0A0E14"))

                    isFocusable = true
                    isFocusableInTouchMode = true

                    session?.let { s ->
                        attachSession(s)
                    }

                    terminalView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                session?.let { s ->
                    if (view.currentSession != s) {
                        view.attachSession(s)
                    }
                }
            }
        )

        FloatingActionButton(
            onClick = {
                terminalView?.let { tv ->
                    tv.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(44.dp),
            shape = CircleShape,
            containerColor = MeowGold,
            contentColor = MeowBg
        ) {
            Icon(
                Icons.Default.Keyboard,
                contentDescription = "Show keyboard",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ExtraKeysBar(
    onKey: (String) -> Unit
) {
    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "-", "/", "|", "~", "_")

    Surface(
        color = MeowSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            keys.forEach { key ->
                Surface(
                    modifier = Modifier
                        .height(38.dp)
                        .widthIn(min = 42.dp)
                        .clickable { onKey(key) },
                    color = MeowCard,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = key,
                            color = MeowText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConsoleWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webViewKey by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(webViewKey) {
        isLoading = true
        loadError = null
        delay(15_000)
        if (isLoading && loadError == null) {
            android.util.Log.w("ConsoleWebView", "Loading timeout after 15s, url=$url")
            val wv = webView
            if (wv != null) {
                val wvUrl = wv.url
                val progress = wv.progress
                android.util.Log.w("ConsoleWebView",
                    "Timeout state: webview.url=$wvUrl progress=$progress width=${wv.width} height=${wv.height}")
            }
            isLoading = false
            loadError = context.getString(R.string.term_load_timeout)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(webViewKey) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode =
                            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = settings.userAgentString + " MeowHub/1.0"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                startUrl: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                android.util.Log.i("ConsoleWebView", "onPageStarted: $startUrl")
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                finishedUrl: String?
                            ) {
                                android.util.Log.i("ConsoleWebView",
                                    "onPageFinished: $finishedUrl w=${view?.width} h=${view?.height}")
                                isLoading = false
                                loadError = null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                val reqUrl = request?.url
                                val code = error?.errorCode
                                val desc = error?.description
                                android.util.Log.e("ConsoleWebView",
                                    "onReceivedError: url=$reqUrl code=$code desc=$desc isMainFrame=${request?.isForMainFrame}")
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = ctx.getString(R.string.term_load_failed, code.toString(), desc?.toString() ?: "")
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                android.util.Log.e("ConsoleWebView",
                                    "onReceivedHttpError: ${request?.url} status=${errorResponse?.statusCode} isMainFrame=${request?.isForMainFrame}")
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = ctx.getString(R.string.term_http_error, errorResponse?.statusCode ?: 0, errorResponse?.reasonPhrase ?: "")
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                android.util.Log.e("ConsoleWebView",
                                    "onReceivedSslError: ${error?.url} type=${error?.primaryError}")
                                handler?.cancel()
                                isLoading = false
                                loadError = ctx.getString(R.string.term_ssl_error, error?.primaryError?.toString() ?: "")
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                android.util.Log.d("ConsoleWebView",
                                    "shouldOverrideUrlLoading: ${request?.url}")
                                return false
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                val crashed = detail?.didCrash() ?: true
                                val priority =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                                        detail?.rendererPriorityAtExit() else -1
                                android.util.Log.e("ConsoleWebView",
                                    "onRenderProcessGone: crashed=$crashed priority=$priority")
                                view?.destroy()
                                webView = null
                                isLoading = false
                                loadError =
                                    if (crashed) ctx.getString(R.string.term_webview_crashed)
                                    else ctx.getString(R.string.term_webview_recycled)
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                val level = consoleMessage?.messageLevel()
                                val msg = consoleMessage?.message()
                                val src = consoleMessage?.sourceId()
                                val line = consoleMessage?.lineNumber()
                                when (level) {
                                    android.webkit.ConsoleMessage.MessageLevel.ERROR ->
                                        android.util.Log.e("ConsoleWebView", "JS ERROR: $msg [$src:$line]")
                                    android.webkit.ConsoleMessage.MessageLevel.WARNING ->
                                        android.util.Log.w("ConsoleWebView", "JS WARN: $msg [$src:$line]")
                                    else ->
                                        android.util.Log.d("ConsoleWebView", "JS: $msg [$src:$line]")
                                }
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                android.util.Log.d("ConsoleWebView", "loading progress: $newProgress%")
                            }
                        }

                        WebView.setWebContentsDebuggingEnabled(true)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        android.util.Log.i("ConsoleWebView",
                            "loadUrl: $url | webview created, size will be measured on layout")

                        post { loadUrl(url) }

                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    android.util.Log.d("ConsoleWebView",
                        "AndroidView update: w=${view.width} h=${view.height} url=${view.url}")
                }
            )
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeowBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MeowGold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.term_loading_console),
                        color = MeowTextDim,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (loadError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeowBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        stringResource(R.string.term_console_connect_failed),
                        color = Color(0xFFFF6B6B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        loadError!!,
                        color = MeowTextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "URL: $url",
                        color = MeowTextDim.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            loadError = null
                            isLoading = true
                            if (webView != null) {
                                webView?.loadUrl(url)
                            } else {
                                webViewKey++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeowGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MeowBg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.term_btn_retry), color = MeowBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MeowGold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MeowTextDim, fontSize = 15.sp)
        }
    }
}

@Composable
private fun InstallContent(onInstall: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeowCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🐱",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.term_env_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeowGold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.term_env_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeowTextDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeowGold),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        stringResource(R.string.term_btn_install_now),
                        color = MeowBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallingContent(progress: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeowCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MeowGold,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.term_initializing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeowText
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeowTextDim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MeowCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.term_install_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeowTextDim,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeowGold),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        stringResource(R.string.term_btn_retry),
                        color = MeowBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
