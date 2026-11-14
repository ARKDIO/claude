# YT 다운로더 (개인용 안드로이드 앱)

유튜브 주소를 넣으면 **최고화질 영상**을 휴대폰 **갤러리**에, **소리만** 받으면 **내 파일**에 저장해 주는 개인용 앱이에요.

## 기능
- 🎬 **영상 다운로드 (최고화질)**: 가장 높은 해상도와 프레임의 영상(4K/8K, 60fps, HDR 포함)에 가장 좋은 소리를 합쳐서 MP4로 저장해요
  → `Movies/YTDownloader` (갤러리에 표시돼요)
- 🎵 **소리만 다운로드**: 가장 좋은 음질의 오디오를 M4A로 저장해요
  → `Download/YTDownloader` (내 파일에 표시돼요)
- 유튜브 앱에서 **공유 → YT 다운로더**를 누르면 주소가 자동으로 들어가요
- 알림창에 진행률이 보이고, 알림에서 바로 취소할 수 있어요
- **엔진 업데이트** 버튼: 유튜브가 바뀌어서 다운로드가 안 될 때 눌러 주세요 (yt-dlp 최신 버전으로 업데이트돼요)

## 설치 방법 (APK 받기)
1. 이 저장소의 **Actions** 탭 → **Build APK** → 가장 최근 성공한 실행을 열어요
2. 아래쪽 **Artifacts**에서 `YTDownloader-apk`를 받아 압축을 풀어요
3. 대부분의 휴대폰은 **`YTDownloader-arm64.apk`**를 설치하면 돼요 (아주 오래된 기종이면 `arm32`)
4. 설치할 때 "출처를 알 수 없는 앱 설치 허용"을 한 번 켜 주세요

> `v1.0.0`처럼 `v`로 시작하는 태그를 푸시하면 **Releases**에 APK가 자동으로 올라가서 휴대폰에서 바로 받기가 더 편해요.

## 기술 구성
- Kotlin + Jetpack Compose (Material 3)
- [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (yt-dlp + ffmpeg + QuickJS 내장)
- 저장: MediaStore (Android 10 이상은 저장 권한이 필요 없어요)
- 최소 Android 7.0 (API 24)

## 직접 빌드하기
```bash
./gradlew assembleDebug
# 결과: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

`app/debug.keystore`는 새 빌드를 기존 앱 위에 덮어서 설치할 수 있게 고정해 둔 **디버그용 서명 키**예요.

## 주의
개인 보관용으로만 사용해 주세요. 유튜브 약관과 저작권을 지키는 범위에서 본인 영상이나 이용이 허락된 영상에만 사용하는 걸 권장해요.
