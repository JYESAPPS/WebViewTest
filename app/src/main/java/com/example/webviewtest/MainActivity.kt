package com.example.webviewtest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import android.provider.MediaStore
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.FileProvider
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.sdk.common.util.Utility;
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val REQUEST_IMAGE_CAPTURE = 1001
    private var imageUri: Uri? = null


    fun getWebView(): WebView {
        return webView
    }

    private fun handleKakaoRedirect(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.toString().startsWith("yourapp://oauth")) {
            val code = data.getQueryParameter("code")
            if (code != null) {
                Log.d("KakaoLogin", "카카오 인증 코드 수신: $code")

                // ✅ WebView 안에 있는 React로 인증 코드 전달
                webView.evaluateJavascript("window.kakaoLoginComplete('$code');", null)
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KakaoSdk.init(this, "f5a9e17194ba85545dc8f9cdb66928ed")

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

        val pref = getSharedPreferences("auth", MODE_PRIVATE)
        val savedToken = pref.getString("access_token", null)

        if (savedToken != null) {
            // 자동 로그인 시도
            webView.evaluateJavascript("window.autoLogin('$savedToken')", null)
        }


        webView.loadUrl("http://www.wizmarket.ai:53003/ads/login")
        handleKakaoRedirect(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleKakaoRedirect(intent)
    }


    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = File.createTempFile("camera_", ".jpg", cacheDir)

        imageUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider", // 예: com.example.webviewtest.fileprovider
            photoFile
        )

        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            imageUri?.let { uri ->
                // ✅ 웹뷰로 이미지 URI 전달
                webView.evaluateJavascript("window.receiveCameraImage('${uri.toString()}')", null)
            }
        }
    }


}
