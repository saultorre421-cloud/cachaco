package com.example

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var lastBackPressElapsedMs: Long = 0L

    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val result = if (uri != null) arrayOf(uri) else null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val appBackgroundColor = Color.parseColor(APP_BACKGROUND_HEX)

        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(appBackgroundColor)
        }

        webView = createConfiguredWebView(appBackgroundColor)
        rootContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, windowInsets ->
            val safeBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                safeBars.left,
                safeBars.top,
                safeBars.right,
                safeBars.bottom
            )
            windowInsets
        }

        setContentView(rootContainer)
        ViewCompat.requestApplyInsets(rootContainer)

        setupDoubleBackToExit()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(ASSET_INDEX_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(backgroundColor: Int): WebView {
        return WebView(this).apply {
            setBackgroundColor(backgroundColor)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            addJavascriptInterface(AndroidExportBridge(this@MainActivity), JS_BRIDGE_NAME)

            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val mimeType = fileChooserParams
                        ?.acceptTypes
                        ?.firstOrNull { it.isNotBlank() }
                        ?: DEFAULT_IMAGE_MIME_TYPE

                    return try {
                        getContentLauncher.launch(mimeType)
                        true
                    } catch (_: ActivityNotFoundException) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = null
                        Toast.makeText(
                            this@MainActivity,
                            R.string.error_open_gallery,
                            Toast.LENGTH_SHORT
                        ).show()
                        false
                    }
                }
            }
        }
    }

    private fun setupDoubleBackToExit() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBackPressElapsedMs <= EXIT_BACK_WINDOW_MS) {
                        isEnabled = false
                        finish()
                    } else {
                        lastBackPressElapsedMs = now
                        Toast.makeText(
                            this@MainActivity,
                            R.string.press_back_again_to_exit,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface(JS_BRIDGE_NAME)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val ASSET_INDEX_URL = "file:///android_asset/index.html"
        private const val JS_BRIDGE_NAME = "AndroidExport"
        private const val DEFAULT_IMAGE_MIME_TYPE = "image/*"
        private const val APP_BACKGROUND_HEX = "#F1F5F9"
        private const val EXIT_BACK_WINDOW_MS = 2000L
    }
}
