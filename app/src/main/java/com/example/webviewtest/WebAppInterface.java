package com.example.webviewtest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.kakao.sdk.user.UserApiClient;

import java.io.File;
import java.io.FileOutputStream;

public class WebAppInterface {
    Context mContext;
    private Activity mActivity;

    public WebAppInterface(Context context) {
        mContext = context;
    }

    public WebAppInterface(Activity activity) {
        this.mActivity = activity;
        this.mContext = activity.getApplicationContext();
    }

    @JavascriptInterface
    public void saveToken(String accessToken, String refreshToken) {
        mContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .apply();
    }

    @JavascriptInterface
    public String getToken() {
        return mContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .getString("access_token", null);
    }

    @JavascriptInterface
    public String getRefreshToken() {
        return mContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .getString("refresh_token", null);
    }

    @JavascriptInterface
    public void openCamera() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).openCamera();  // ✅ MainActivity의 openCamera 호출
        }
    }


    @JavascriptInterface
    public void shareInstagramBase64(String base64Image, String caption) {
        new Thread(() -> {
            try {
                // 1. 이미지 처리
                String base64 = base64Image.split(",")[1];
                byte[] imageData = Base64.decode(base64, Base64.DEFAULT);
                File cachePath = new File(mContext.getCacheDir(), "images");
                cachePath.mkdirs();
                File file = new File(cachePath, "shared_image.png");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(imageData);
                fos.close();

                Uri contentUri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".fileprovider",
                        file
                );

                // 2. 클립보드에 caption 복사
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Instagram Caption", caption);
                clipboard.setPrimaryClip(clip);

                // 3. 인스타그램 공유 인텐트
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setPackage("com.instagram.android");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                mContext.startActivity(shareIntent);

                // 4. 안내 메시지
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "텍스트가 복사되었습니다. 인스타그램에 붙여넣기 해주세요.", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "Instagram 공유 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    @JavascriptInterface
    public void shareKakaoTalk(String base64Image) {
        new Thread(() -> {
            try {
                // 1. 이미지 디코딩 및 저장
                String base64 = base64Image.split(",")[1];
                byte[] imageData = Base64.decode(base64, Base64.DEFAULT);
                File cachePath = new File(mContext.getCacheDir(), "images");
                cachePath.mkdirs();
                File file = new File(cachePath, "shared_kakao_image.png");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(imageData);
                fos.close();

                Uri contentUri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".fileprovider",
                        file
                );

                // 2. 카카오톡 공유 인텐트
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setPackage("com.kakao.talk");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                mContext.startActivity(shareIntent);

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "카카오톡 공유 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    @JavascriptInterface
    public void startKakaoLogin() {
        new Handler(Looper.getMainLooper()).post(() -> {
            // 카카오톡 로그인 → 안되면 계정 로그인으로 fallback
            if (UserApiClient.getInstance().isKakaoTalkLoginAvailable(mContext)) {
                UserApiClient.getInstance().loginWithKakaoTalk(mContext, (oauthToken, error) -> {
                    if (error != null) {
                        Log.e("KakaoLogin", "카카오톡 로그인 실패, 계정 로그인 시도", error);
                        loginWithKakaoAccountFallback(); // 🔁 fallback 시도
                    } else if (oauthToken != null) {
                        sendTokenToWebView(oauthToken.getAccessToken());
                    }
                    return null; // ✅ 이거 꼭 추가
                });
            } else {
                // ❗ 카카오톡 미설치 → 바로 계정 로그인
                loginWithKakaoAccountFallback();
            }
        });
    }

    private void loginWithKakaoAccountFallback() {
        UserApiClient.getInstance().loginWithKakaoAccount(mContext, (oauthToken, error) -> {
            if (error != null) {
                Log.e("KakaoLogin", "계정 로그인 실패", error);
            } else if (oauthToken != null) {
                sendTokenToWebView(oauthToken.getAccessToken());
            }
            return null;
        });
    }

    private void sendTokenToWebView(String token) {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).runOnUiThread(() -> {
                ((MainActivity) mContext).getWebView().evaluateJavascript(
                        "window.kakaoLoginComplete('" + token + "')", null
                );
            });
        }
    }
}
