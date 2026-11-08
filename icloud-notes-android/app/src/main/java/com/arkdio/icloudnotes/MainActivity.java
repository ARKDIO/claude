package com.arkdio.icloudnotes;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * iCloud 메모를 전체 화면 WebView로 띄우는 앱.
 *
 * 보안 원칙
 *  - Apple / iCloud 도메인만 앱 안에서 열고, 나머지는 외부 브라우저로 보내거나 차단해요.
 *  - HTTPS만 허용, SSL 오류는 무조건 차단해요.
 *  - 앱을 열 때와 백그라운드에서 일정 시간 지난 뒤 돌아올 때 지문/화면잠금으로 본인 확인을 해요.
 *  - JavaScript 브리지, 파일 접근, 위치·카메라·마이크 권한은 모두 사용하지 않아요.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://www.icloud.com/notes/";

    /** 백그라운드에 이 시간 이상 있었다가 돌아오면 다시 잠가요. */
    private static final long RELOCK_TIMEOUT_MS = 60_000L;

    private static final int REQUEST_FILE_CHOOSER = 1001;

    /** 앱 안에서 열어도 되는 도메인 (하위 도메인 포함). */
    private static final String[] ALLOWED_HOST_SUFFIXES = {
            "icloud.com",
            "apple.com",
            "apple-cloudkit.com",
            "icloud-content.com",
            "cdn-apple.com",
            "mzstatic.com",
    };

    private ViewGroup root;
    private WebView webView;
    private View lockOverlay;
    private TextView lockTitle;
    private TextView lockMessage;
    private Button unlockButton;
    private View errorOverlay;

    private boolean unlocked = false;
    private boolean contentLoaded = false;
    private boolean authInProgress = false;
    private boolean autoPromptPending = true;
    /** 파일 선택처럼 우리가 직접 띄운 화면 때문에 잠깐 나가는 경우에는 잠그지 않아요. */
    private boolean internalActivityOpen = false;
    private long backgroundSince = 0L;

    private CancellationSignal authCancel;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 최근 앱 목록 미리보기에 메모 내용이 찍히지 않게 해요 (Android 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(false);
        }

        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        webView = findViewById(R.id.webview);
        lockOverlay = findViewById(R.id.lock_overlay);
        lockTitle = findViewById(R.id.lock_title);
        lockMessage = findViewById(R.id.lock_message);
        unlockButton = findViewById(R.id.unlock_button);
        errorOverlay = findViewById(R.id.error_overlay);

        unlockButton.setOnClickListener(v -> requestUnlock());
        findViewById(R.id.retry_button).setOnClickListener(v -> {
            errorOverlay.setVisibility(View.GONE);
            if (webView != null) {
                if (webView.getUrl() == null) {
                    webView.loadUrl(START_URL);
                } else {
                    webView.reload();
                }
            }
        });

        setupFullscreen();
        setupInsets();
        setupWebView();
        lock();
    }

    // ---------------------------------------------------------------------------------------
    // 전체 화면
    // ---------------------------------------------------------------------------------------

    private void setupFullscreen() {
        getWindow().setDecorFitsSystemWindows(false);
        hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    /** 카메라 구멍(컷아웃)과 키보드 영역만큼 여백을 줘서 내용이 가려지지 않게 해요. */
    private void setupInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(cutout.left, cutout.top, cutout.right, Math.max(ime.bottom, cutout.bottom));
            return WindowInsets.CONSUMED;
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    // ---------------------------------------------------------------------------------------
    // WebView 보안 설정
    // ---------------------------------------------------------------------------------------

    private void setupWebView() {
        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);          // iCloud 웹앱 동작에 필요
        s.setDomStorageEnabled(true);          // 로그인 상태 유지에 필요
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        //noinspection deprecation
        s.setAllowFileAccessFromFileURLs(false);
        //noinspection deprecation
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        s.setUserAgentString(buildBrowserUserAgent(s.getUserAgentString()));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        // Apple 로그인 창(idmsa.apple.com)이 iframe 으로 열려서 필요해요.
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new SecureWebViewClient());
        webView.setWebChromeClient(new SecureChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                Toast.makeText(this, R.string.download_blocked, Toast.LENGTH_SHORT).show());
    }

    /**
     * WebView 표시("; wv", "Version/4.0", 빌드 번호)를 빼서 일반 모바일 Chrome 처럼 보이게 해요.
     * iCloud 가 "지원하지 않는 브라우저" 화면을 띄우지 않게 하기 위함이에요.
     */
    static String buildBrowserUserAgent(String defaultUa) {
        if (defaultUa == null) {
            return null;
        }
        return defaultUa
                .replace("; wv)", ")")
                .replaceAll(" Build/[^;)]+", "")
                .replaceAll("Version/\\d+(\\.\\d+)* ", "");
    }

    static boolean isAllowedHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_HOST_SUFFIXES) {
            if (h.equals(suffix) || h.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private void openInExternalBrowser(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        intent.setSelector(null);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // 열 수 있는 브라우저가 없으면 아무것도 하지 않아요.
        }
    }

    private class SecureWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

            if ("https".equals(scheme) && isAllowedHost(uri.getHost())) {
                return false; // Apple/iCloud 페이지는 앱 안에서 열기
            }

            // 그 외 링크: 사용자가 직접 누른 일반 웹 링크만 외부 브라우저로, 나머지(intent:, file:, javascript: 등)는 차단
            if (request.isForMainFrame() && request.hasGesture()
                    && ("https".equals(scheme) || "http".equals(scheme))) {
                openInExternalBrowser(uri);
            }
            return true;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); // 인증서 오류는 절대 무시하지 않아요.
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                errorOverlay.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // 렌더러가 죽어도 앱이 강제종료되지 않게 WebView 를 정리하고 다시 시작해요.
            if (webView != null) {
                root.removeView(webView);
                webView.destroy();
                webView = null;
            }
            recreate();
            return true;
        }
    }

    private class SecureChromeClient extends WebChromeClient {

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny(); // 카메라·마이크 등 모든 권한 요청 거부
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            // 메모에 사진 등을 첨부할 때 시스템 파일 선택기만 사용해요 (저장소 권한 불필요).
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            try {
                internalActivityOpen = true;
                startActivityForResult(params.createIntent(), REQUEST_FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException e) {
                internalActivityOpen = false;
                filePathCallback = null;
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_CHOOSER) {
            internalActivityOpen = false;
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // 앱 잠금 (지문 / 화면 잠금)
    // ---------------------------------------------------------------------------------------

    private void lock() {
        unlocked = false;
        autoPromptPending = true;
        if (webView != null) {
            webView.setVisibility(View.INVISIBLE);
        }
        lockTitle.setText(R.string.locked_title);
        lockMessage.setText(R.string.locked_message);
        unlockButton.setText(R.string.unlock);
        unlockButton.setOnClickListener(v -> requestUnlock());
        lockOverlay.setVisibility(View.VISIBLE);
    }

    private void onUnlocked() {
        unlocked = true;
        backgroundSince = 0L;
        lockOverlay.setVisibility(View.GONE);
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            if (!contentLoaded) {
                contentLoaded = true;
                webView.loadUrl(START_URL);
            }
        }
    }

    private void requestUnlock() {
        autoPromptPending = false;
        if (authInProgress) {
            return;
        }

        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            // 화면 잠금이 없는 폰에서는 보호가 불가능하므로 열지 않아요.
            // 설정에서 돌아오면 다시 확인하도록 자동 확인 플래그를 켜 둬요.
            autoPromptPending = true;
            lockTitle.setText(R.string.no_screen_lock_title);
            lockMessage.setText(R.string.no_screen_lock_message);
            unlockButton.setText(R.string.open_security_settings);
            unlockButton.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                } catch (ActivityNotFoundException ignored) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
            return;
        }

        lockTitle.setText(R.string.locked_title);
        lockMessage.setText(R.string.locked_message);
        unlockButton.setText(R.string.unlock);
        unlockButton.setOnClickListener(v -> requestUnlock());

        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.unlock_prompt_title))
                .setSubtitle(getString(R.string.unlock_prompt_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build();

        authCancel = new CancellationSignal();
        authInProgress = true;
        try {
            prompt.authenticate(authCancel, getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            authInProgress = false;
                            onUnlocked();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            authInProgress = false;
                            if (!unlocked) {
                                lockMessage.setText(errString != null
                                        ? errString : getString(R.string.auth_failed));
                            }
                        }
                    });
        } catch (RuntimeException e) {
            authInProgress = false;
            lockMessage.setText(R.string.auth_failed);
        }
    }

    // ---------------------------------------------------------------------------------------
    // 생명주기
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        if (unlocked && backgroundSince > 0L
                && SystemClock.elapsedRealtime() - backgroundSince >= RELOCK_TIMEOUT_MS) {
            lock();
        }
        backgroundSince = 0L;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (webView != null) {
            webView.onResume();
        }
        if (!unlocked && autoPromptPending && !authInProgress) {
            requestUnlock();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush(); // 로그인 세션을 디스크에 바로 저장
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!authInProgress && !internalActivityOpen) {
            backgroundSince = SystemClock.elapsedRealtime();
            if (!unlocked) {
                autoPromptPending = true; // 잠긴 채로 나갔다 오면 다시 자동으로 물어봐요
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (authCancel != null) {
            authCancel.cancel();
        }
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (unlocked && webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            // 앱을 종료하지 않고 뒤로 보내서 세션과 화면 상태를 유지해요.
            moveTaskToBack(true);
        }
    }
}
