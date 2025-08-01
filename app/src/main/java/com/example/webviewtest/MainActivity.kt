package com.example.webviewtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.kakao.sdk.common.KakaoSdk
import java.io.File
import androidx.appcompat.app.AlertDialog
import com.navercorp.nid.NaverIdLoginSDK

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val REQUEST_CAMERA_PERMISSION = 1001
    private val REQUEST_IMAGE_CAPTURE = 1002  // 기존과 동일
    private val REQUEST_GALLERY = 1003
    private var imageUri: Uri? = null
    private lateinit var webAppInterface: WebAppInterface



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

        NaverIdLoginSDK.initialize(
            this,
            "lwb1w99Kh03rUiUlrdRV",
            "Gx1QngKlF0",
            "Wiz AD"
        )

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

        webAppInterface = WebAppInterface(this)
        webView.addJavascriptInterface(webAppInterface, "Android")


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
        AlertDialog.Builder(this)
            .setTitle("앱 종료")
            .setMessage("앱을 종료하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                finishAffinity()  // ✅ 앱 완전 종료
            }
            .setNegativeButton("취소", null)  // 아무 동작 없음
            .show()
    }




    private fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken

            // WebView로 토큰 전달
            webView.evaluateJavascript("window.googleLoginComplete('$idToken')", null)

        } catch (e: ApiException) {
            Log.e("GoogleLogin", "❌ 로그인 실패 statusCode=${e.statusCode}, message=${e.message}")
            Toast.makeText(this, "❌ 로그인 실패: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }






    fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            return
        }
        launchCameraIntent()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent()  // ✅ 권한 허용되면 바로 카메라 실행
            } else {
                Toast.makeText(this, "❌ 카메라 권한이 거부되었습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun launchCameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = File.createTempFile("camera_", ".jpg", cacheDir)
            imageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",  // ex. com.example.webviewtest.fileprovider
                photoFile
            )

            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)  // ✅ 사진을 해당 경로에 저장

            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(this, "❌ 카메라 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            webAppInterface.setImageUri(imageUri)
            webAppInterface.sendCapturedImage()

        } else if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            val selectedImageUri = data.data
            imageUri = selectedImageUri
            webAppInterface.setImageUri(selectedImageUri)
            webAppInterface.sendGalleryImage()

        } else if (requestCode == WebAppInterface.GOOGLE_SIGN_IN_REQUEST_CODE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleGoogleSignInResult(task)

        } else {
            Toast.makeText(this, "❌ 사진 선택 실패 또는 취소됨", Toast.LENGTH_SHORT).show()
        }
    }










}
