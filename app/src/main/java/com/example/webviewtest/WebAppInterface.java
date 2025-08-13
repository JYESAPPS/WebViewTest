package com.example.webviewtest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.kakao.sdk.user.UserApiClient;
import android.content.Intent;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
//import com.navercorp.nid.NaverIdLoginSDK;
//import com.navercorp.nid.oauth.OAuthLoginCallback;
import org.json.JSONArray;
import java.util.ArrayList;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;

import java.io.OutputStream;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class WebAppInterface {
    private Context mContext;
    private Activity mActivity;
    private Uri imageUri = null;

    public static final int GOOGLE_SIGN_IN_REQUEST_CODE = 1001;
    public void setImageUri(Uri uri) {
        this.imageUri = uri;
    }


    public WebAppInterface(Context context) {
        mContext = context;
    }

    public WebAppInterface(Activity activity) {
        this.mActivity = activity;
        this.mContext = activity;
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


    // 카톡 로그인
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


    // 구글 로그인
    @JavascriptInterface
    public void startGoogleLogin() {
        new Handler(Looper.getMainLooper()).post(() -> {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken("621843053596-0v9o72kp1u1e03vb64j7nfbjl5e9ptkd.apps.googleusercontent.com") // ✅ 반드시 Android용 클라이언트 ID 사용
                    .build();

            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(mContext, gso);
            Intent signInIntent = googleSignInClient.getSignInIntent();

            if (mContext instanceof Activity) {
                ((Activity) mContext).startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE);
            }
        });
    }

    // 네이버 로그인
//    @JavascriptInterface
//    public void startNaverLogin() {
//        if (!(mContext instanceof MainActivity)) return;
//
//        Activity activity = (Activity) mContext;
//
//        NaverIdLoginSDK.INSTANCE.authenticate(activity, new OAuthLoginCallback() {
//            @Override
//            public void onSuccess() {
//                String accessToken = NaverIdLoginSDK.INSTANCE.getAccessToken();
//                Log.d("✅ NaverLogin", "AccessToken: " + accessToken);
//
//                activity.runOnUiThread(() -> {
//                    ((MainActivity) mContext).getWebView().evaluateJavascript(
//                            "window.naverLoginComplete('" + accessToken + "')", null
//                    );
//                });
//            }
//
//            @Override
//            public void onFailure(int httpStatus, String message) {
//                Log.e("❌ NaverLogin", "Failure (" + httpStatus + "): " + message);
//            }
//
//            @Override
//            public void onError(int errorCode, String message) {
//                Log.e("❌ NaverLogin", "Error (" + errorCode + "): " + message);
//            }
//        });
//    }





    // 카메라 열기
    @JavascriptInterface
    public void openCamera() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).openCamera();
        }
    }


    @JavascriptInterface
    public void sendCapturedImage() {
        if (imageUri == null) return;

        try {
            InputStream inputStream = mContext.getContentResolver().openInputStream(imageUri);
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            inputStream.close();

            // ✅ 1. 줄바꿈 없이 인코딩
            String base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String dataUri = "data:image/jpeg;base64," + base64Image;

            // ✅ 2. JS로 넘기기 전에 URI-safe 인코딩
            String encodedDataUri = Uri.encode(dataUri);

            // ✅ 3. JS에서 decodeURIComponent(...)로 복원
            ((MainActivity) mContext).runOnUiThread(() -> {
                ((MainActivity) mContext).getWebView().evaluateJavascript(
                        "window.receiveCameraImage(decodeURIComponent('" + encodedDataUri + "'))", null
                );
            });
        } catch (Exception e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(mContext, "❌ 이미지 처리 중 오류 발생", Toast.LENGTH_SHORT).show()
            );
        }
    }

    // 갤러리 열기
    @JavascriptInterface
    public void openGallery() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).openGallery();
        }
    }

    // 선택 이미지 전송
    @JavascriptInterface
    public void sendGalleryImage() {
        if (imageUri == null) return;

        try {
            InputStream inputStream = mContext.getContentResolver().openInputStream(imageUri);
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            inputStream.close();

            // ✅ 1. 줄바꿈 없이 인코딩
            String base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String dataUri = "data:image/jpeg;base64," + base64Image;

            // ✅ 2. JS로 넘기기 전에 URI-safe 인코딩
            String encodedDataUri = Uri.encode(dataUri);

            // ✅ 3. JS에서 decodeURIComponent(...)로 복원
            ((MainActivity) mContext).runOnUiThread(() -> {
                ((MainActivity) mContext).getWebView().evaluateJavascript(
                        "window.receiveCameraImage(decodeURIComponent('" + encodedDataUri + "'))", null
                );
            });
        } catch (Exception e) {
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(mContext, "❌ 이미지 처리 중 오류 발생", Toast.LENGTH_SHORT).show()
            );
        }
    }


    // 카메라 or 갤러리 선택 취소
    @JavascriptInterface
    public void notifyImageSelectionCancelled() {
        if (mActivity instanceof MainActivity) {
            MainActivity main = (MainActivity) mActivity;
            main.runOnUiThread(() -> {
                WebView webView = main.getWebView();
                webView.evaluateJavascript("window.onCameraCancelled()", null);
            });
        }
    }



    // 새창 열기
    @JavascriptInterface
    public void openExternalLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);  // ✅ 정상 동작
    }



    // 피드
    @JavascriptInterface
    public void shareInstagramBase64(String base64Image, String caption) {
        new Thread(() -> {
            try {
                // 0) 인스타그램 설치 확인
                if (!isAppInstalled(INSTAGRAM_PKG)) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Instagram이 설치되어 있지 않습니다. 설치 페이지로 이동합니다.", Toast.LENGTH_SHORT).show()
                    );
                    goToInstagramInstallPage();
                    return;
                }


                // 1) 파일 저장 (파일명 유니크)
                String base64 = base64Image.split(",")[1];
                byte[] imageData = Base64.decode(base64, Base64.DEFAULT);
                File cacheDir = new File(mContext.getCacheDir(), "images");
                cacheDir.mkdirs();
                File file = new File(cacheDir, "ig_feed_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(imageData); }

                Uri uri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".fileprovider",
                        file
                );

                // 2) 캡션 클립보드
                ClipboardManager cb = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Instagram Caption", caption));

                // 3) 기본 SEND 인텐트
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/*");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                send.setPackage("com.instagram.android");

                // 4) 권한/ClipData
                mContext.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri("shared_image", uri));

                // 5) 인스타그램 내 액티비티 탐색 → Feed 핸들러 우선 지정
                PackageManager pm = mContext.getPackageManager();
                List<ResolveInfo> targets = pm.queryIntentActivities(send, 0);

                ComponentName feedComponent = null;
                for (ResolveInfo ri : targets) {
                    String cls = ri.activityInfo.name;
                    String clsLower = cls.toLowerCase();
                    // 스토리/릴스/디렉트가 아닌 핸들러를 선호
                    boolean isInstagram = "com.instagram.android".equals(ri.activityInfo.packageName);
                    boolean looksLikeFeed =
                            cls.contains("ShareHandlerActivity") &&
                                    !clsLower.contains("story") &&
                                    !clsLower.contains("reel") &&
                                    !clsLower.contains("direct");

                    if (isInstagram && looksLikeFeed) {
                        feedComponent = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                        break;
                    }
                }

                if (feedComponent != null) {
                    send.setComponent(feedComponent);   // ✅ 피드 컴포저로 바로 진입
                }

                // (선택) 항상 새 진입 느낌으로
                Intent chooser = Intent.createChooser(send, "Instagram에 공유");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);

                // 6) 안내
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "캡션이 복사되었습니다. 인스타그램에서 붙여넣기 하세요.", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                Log.e("InstagramShare", "Feed 공유 실패", e);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "Instagram 공유 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    // 스토리
    @JavascriptInterface
    public void shareInstagramImageOnly(String base64Image) {
        new Thread(() -> {
            try {
                // 0) 인스타그램 설치 확인
                if (!isAppInstalled(INSTAGRAM_PKG)) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(mContext, "Instagram이 설치되어 있지 않습니다. 설치 페이지로 이동합니다.", Toast.LENGTH_SHORT).show()
                    );
                    goToInstagramInstallPage();
                    return;
                }

                // 1) 파일 저장 (파일명 유니크)
                String base64 = base64Image.split(",")[1];
                byte[] imageData = Base64.decode(base64, Base64.DEFAULT);
                File cacheDir = new File(mContext.getCacheDir(), "images");
                cacheDir.mkdirs();
                File file = new File(cacheDir, "ig_story_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(imageData); }

                Uri uri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".fileprovider",
                        file
                );

                // 2) 권한 부여
                mContext.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // 3) 스토리 전용 인텐트
                Intent story = new Intent("com.instagram.share.ADD_TO_STORY");
                story.setDataAndType(uri, "image/*");
                story.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                story.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // 없으면 폴백
                if (story.resolveActivity(mContext.getPackageManager()) != null) {
                    mContext.startActivity(story);
                } else {
                    // 구형 기기 폴백 (일반 공유)
                    Intent fallback = new Intent(Intent.ACTION_SEND);
                    fallback.setType("image/*");
                    fallback.putExtra(Intent.EXTRA_STREAM, uri);
                    fallback.setPackage("com.instagram.android");
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(fallback);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // 인스타 설치 여부 확인용
    private static final String INSTAGRAM_PKG = "com.instagram.android";
    private static final String PLAY_STORE_PKG = "com.android.vending";

    private boolean isAppInstalled(String pkg) {
        try {
            PackageManager pm = mContext.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0));
            } else {
                pm.getPackageInfo(pkg, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // 인스타 플레이스토어로 이동
    private void goToInstagramInstallPage() {
        // 1) Play 스토어 앱으로 시도
        try {
            Uri marketUri = Uri.parse("market://details?id=" + INSTAGRAM_PKG);
            Intent market = new Intent(Intent.ACTION_VIEW, marketUri);
            market.setPackage(PLAY_STORE_PKG); // Play 스토어로 강제
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (market.resolveActivity(mContext.getPackageManager()) != null) {
                mContext.startActivity(market);
                return;
            }
        } catch (Exception ignore) {}

        // 2) 웹 폴백(Play 스토어가 없는 기기 등)
        Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + INSTAGRAM_PKG);
        Intent web = new Intent(Intent.ACTION_VIEW, webUri);
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(web);
    }

    // 카카오톡
    @JavascriptInterface
    public void shareKakaoTalk(String base64Image) {
        new Thread(() -> {
            try {
                // 1) 디코딩 & 유니크 파일명
                String base64 = base64Image.split(",")[1];
                byte[] imageData = Base64.decode(base64, Base64.DEFAULT);
                File cachePath = new File(mContext.getCacheDir(), "images");
                cachePath.mkdirs();
                String fileName = "shared_kakao_image_" + System.currentTimeMillis() + ".png";
                File file = new File(cachePath, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(imageData);
                }

                // 2) 콘텐츠 URI
                Uri contentUri = FileProvider.getUriForFile(
                        mContext,
                        mContext.getPackageName() + ".fileprovider",
                        file
                );

                // 3) 카톡에 URI 읽기 권한 부여 + ClipData 세팅
                mContext.grantUriPermission(
                        "com.kakao.talk",
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setClipData(ClipData.newRawUri("shared_image", contentUri));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setPackage("com.kakao.talk");
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                // (선택) Chooser 사용
                Intent chooser = Intent.createChooser(shareIntent, "카카오톡으로 공유");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(chooser);

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "카카오톡 공유 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    // 블로그
    @JavascriptInterface
    public void shareBlog(String base64ListJson, String caption) {
        new Thread(() -> {
            try {
                JSONArray base64Array = new JSONArray(base64ListJson);
                ArrayList<Uri> uriList = new ArrayList<>();

                File cacheDir = new File(mContext.getCacheDir(), "blog_images");
                cacheDir.mkdirs();

                for (int i = 0; i < base64Array.length(); i++) {
                    String base64Image = base64Array.getString(i).split(",")[1];
                    byte[] imageData = Base64.decode(base64Image, Base64.DEFAULT);

                    File imageFile = new File(cacheDir, "blog_image_" + i + ".png");
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    fos.write(imageData);
                    fos.close();

                    Uri uri = FileProvider.getUriForFile(
                            mContext,
                            mContext.getPackageName() + ".fileprovider",
                            imageFile
                    );
                    uriList.add(uri);
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "공유 이미지 수: " + uriList.size(), Toast.LENGTH_SHORT).show()
                );

                // 클립보드에 캡션 복사
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Blog Caption", caption);
                clipboard.setPrimaryClip(clip);

                // 공유 인텐트 만들기
                Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.setType("image/*");
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                shareIntent.putExtra(Intent.EXTRA_TEXT, caption);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // 공유 앱 선택
                shareIntent.setPackage("com.nhn.android.blog");
                mContext.startActivity(shareIntent);

                // 안내 메시지
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "텍스트가 복사되었습니다. 블로그에 붙여넣기 해주세요.", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "블로그 공유 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
    // WebAppInterface.java

    // 기기 ID + 토큰 값 전송
    @JavascriptInterface
    public void sendTokenToWeb(String token) {
        if (mActivity instanceof MainActivity) {
            MainActivity main = (MainActivity) mActivity;
            main.runOnUiThread(() -> {
                WebView webView = main.getWebView();  // ✅ 이제 인식됨
                String js = String.format("window.receiveFcmToken('%s');", token);
                webView.evaluateJavascript(js, null);
            });
        }
    }


    // 이미지 저장
    @JavascriptInterface
    public void saveCapturedImageBase64(String base64Data) {
        new Thread(() -> {
            try {
                // base64 prefix 방어
                String base64 = base64Data.contains(",") ? base64Data.split(",")[1] : base64Data;
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);

                String fileName = "captured_image_" + System.currentTimeMillis() + ".png";

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

                // Android 10+(API 29~): 공용 Pictures/YourApp 경로 지정
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YourApp");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }

                ContentResolver resolver = mContext.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new RuntimeException("MediaStore insert failed");

                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os == null) throw new RuntimeException("OutputStream is null");
                    os.write(imageBytes);
                    os.flush();
                }

                // Android 10+: IS_PENDING 해제해서 갤러리에 나타나게
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                } else {
                    // Android 9 이하: 스캐너에 알림 (경로 필요)
                    String path = getRealPathFromUri(uri);
                    if (path != null) {
                        MediaScannerConnection.scanFile(
                                mContext, new String[]{ path }, new String[]{"image/png"}, null
                        );
                    }
                }

                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "✅ 갤러리에 저장됨", Toast.LENGTH_SHORT).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mContext, "❌ 이미지 저장 실패", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    /** content:// → 실제 파일 경로 (API <29에서만 사용, Q이상은 굳이 필요X) */
    private String getRealPathFromUri(Uri uri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        try (Cursor cursor = mContext.getContentResolver().query(uri, proj, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }


}
