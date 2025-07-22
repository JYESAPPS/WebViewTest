package com.example.webviewtest

import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager


class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false) // ✅ 시스템 창과 겹치도록 설정

            window.insetsController?.let { controller ->
                // ✅ 아이콘은 검정색으로 유지
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
                controller.show(WindowInsets.Type.statusBars())
            }

            // ✅ 상태바 배경을 완전히 투명하게
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }



        // ✅ XML에서 WebView 찾기
        webView = findViewById(R.id.webView)

        // ✅ WebChromeClient 커스터마이징: console.log 출력용
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} @ ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                return true
            }
        }

        webView.webViewClient = WebViewClient()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)

        webView.addJavascriptInterface(WebAppInterface(this), "Android")


        webView.loadUrl("http://www.wizmarket.ai:53003/ads/login/MA010120220808570604")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
