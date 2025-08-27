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
import androidx.core.view.WindowCompat
// import com.navercorp.nid.NaverIdLoginSDK
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val REQUEST_CAMERA_PERMISSION = 1001
    private val REQUEST_IMAGE_CAPTURE = 1002  // 기존과 동일
    private val REQUEST_GALLERY = 1003
    private var imageUri: Uri? = null
    private lateinit var webAppInterface: WebAppInterface
    private val NOTIFICATION_PERMISSION_CODE = 1004



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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main) // 딱 한 번만!

        // ✅ 루트 레이아웃에만 인셋 패딩 적용
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }


        KakaoSdk.init(this, "f5a9e17194ba85545dc8f9cdb66928ed")

        //        NaverIdLoginSDK.initialize(
//            this,
//            "lwb1w99Kh03rUiUlrdRV",
//            "Gx1QngKlF0",
//            "Wiz AD"
//        )

        // ✅ XML에서 WebView 찾기
        webView = findViewById(R.id.webView)

        // ✅ WebChromeClient 커스터마이징: console.log 출력용
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} @ ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                return true
            }
        }

        // webView.webViewClient = WebViewClient()

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // ✅ 페이지 로딩 완료 후 토큰 + UUID 전송
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result ?: ""
                        val installationId = getOrCreateInstallationId() // ✅ 추가된 한 줄

                        Log.d("FCM", "토큰 (onPageFinished): $token")
                        Log.d("FCM", "installationId: $installationId")

                        // ✅ JS: token + installationId 두 개 전달
                        webView.evaluateJavascript(
                            "window.receiveFcmToken('$token','$installationId');",
                            null
                        )
                    } else {
                        Log.w("FCM", "토큰 가져오기 실패", task.exception)
                    }

                }
            }
        }


        webView.loadUrl("http://www.wizmarket.ai:53003/ads/login")
        handleKakaoRedirect(intent)


    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleKakaoRedirect(intent)
    }

    private fun getOrCreateInstallationId(): String {
        val pref = getSharedPreferences("app", MODE_PRIVATE)
        var id = pref.getString("installation_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            pref.edit().putString("installation_id", id).apply()
        }
        return id
    }



    // 뒤로가기 키 막기
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



    // 구글 로그인
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



    // 카메라 열기
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

    // 카메라 권한
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
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("FCM", "✅ 알림 권한 허용됨")
            } else {
                Toast.makeText(this, "❌ 알림 권한이 거부되었습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // 카메라 실행
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


    // 갤러리 열기
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
            webAppInterface.notifyImageSelectionCancelled()  // ✅ JS에게도 취소 알림
            Toast.makeText(this, "❌ 사진 선택 실패 또는 취소됨", Toast.LENGTH_SHORT).show()
        }
    }










}
