package com.example.webviewtest

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 여기에 실제 REST API 키를 넣어주세요
        KakaoSdk.init(this, "f5a9e17194ba85545dc8f9cdb66928ed")
    }
}
