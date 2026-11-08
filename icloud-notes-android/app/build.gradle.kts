plugins {
    id("com.android.application")
}

// 서명 정보는 CI 환경변수(GitHub Secrets)로만 주입해요. 키나 비밀번호는 저장소에 절대 커밋하지 않아요.
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.arkdio.icloudnotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arkdio.icloudnotes"
        minSdk = 30
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("VERSION_CODE") ?: "0"}"
    }

    signingConfigs {
        create("release") {
            if (signingKeystorePath != null) {
                storeFile = file(signingKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                // 키가 없을 때만 임시 디버그 키로 서명돼요 (테스트용).
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // APK 안에 의존성 메타데이터 블롭을 넣지 않아요.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// 외부 라이브러리 의존성 없음: 공급망 공격 표면을 최소화해요.
