package com.wawrapper.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int FILE_CHOOSER_REQUEST_CODE = 100;

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private long backPressedTime = 0;
    private SensorManager sensorManager;
    private Sensor proximitySensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#075E54"));
        }

        checkAndRequestPermissions();
        configureWebView();
        startWebSocketService();
        initProximitySensor();
        handleShareIntent(getIntent());

        if (savedInstanceState != null && webView != null) {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(proximityListener);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }

        // Cek via JS apakah user sedang di chat (SPA) atau di chat list
        webView.evaluateJavascript("__WA_wrapper.isInChat()", value -> {
            if ("true".equals(value)) {
                // Masih di chat — back ke chat list via WA internal
                webView.evaluateJavascript("__WA_wrapper.goBackToHomepage()", null);
            } else {
                // Sudah di chat list — tanya konfirmasi exit
                long currentTime = System.currentTimeMillis();
                if (currentTime - backPressedTime > 2000) {
                    backPressedTime = currentTime;
                    new AlertDialog.Builder(this)
                        .setTitle("Keluar Aplikasi?")
                        .setMessage("Aplikasi akan ditutup. Chat dan session tetap aman.")
                        .setPositiveButton("Ya", (dialog, which) -> finish())
                        .setNegativeButton("Tidak", null)
                        .show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (uploadMessage == null) return;
            uploadMessage.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            );
            uploadMessage = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(
                android.view.WindowInsets.Type.statusBars()
                | android.view.WindowInsets.Type.navigationBars()
            );
            getWindow().getInsetsController().setSystemBarsBehavior(
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            };
        }

        boolean needRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(
                this, permissions, PERMISSION_REQUEST_CODE
            );
        }
    }

    private void configureWebView() {
        webView = this.getBridge().getWebView();

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
            ) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }
                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("web.whatsapp.com") || url.contains("whatsapp.com")) {
                    return false;
                }
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(browserIntent);
                } catch (Exception e) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectWAWrapper(view);
            }
        });

        // Performance & storage
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.getSettings().setOffscreenPreRaster(true);

        // Media & WebRTC
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setMixedContentMode(
            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        );

        // === DISABLE ZOOM ===
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);

        // === MINIMIZE BROWSER UX ===
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(false);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.getSettings().setLayoutAlgorithm(
            WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        );

        // Disable long press context menu (browser-like)
        webView.setOnLongClickListener(v -> true);

        // Security
        webView.getSettings().setAllowFileAccess(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webView.getSettings().setForceDark(
                WebSettings.FORCE_DARK_OFF
            );
        }
    }

    private void startWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void initProximitySensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            sensorManager.registerListener(
                proximityListener, proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
            );
        }
    }

    private final SensorEventListener proximityListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] < proximitySensor.getMaximumRange()) {
                getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            } else {
                getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && webView != null) {
                    String js = String.format(
                        "navigator.clipboard.writeText('%s');",
                        sharedText.replace("'", "\\'")
                    );
                    webView.evaluateJavascript(js, null);
                    Toast.makeText(this,
                        "Teks siap di-paste", Toast.LENGTH_SHORT).show();
                }
            } else if (type.startsWith("image/")) {
                Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null && webView != null) {
                    Toast.makeText(this,
                        "Gambar siap di-paste", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void injectWAWrapper(WebView view) {
        String css =
            "div[data-testid=\"sidebar\"]{" +
            "width:100vw!important;max-width:100vw!important;min-width:100vw!important;flex:none!important;}" +
            "div[data-testid=\"conversation-panel\"]{" +
            "width:100vw!important;max-width:100vw!important;min-width:100vw!important;flex:none!important;" +
            "position:fixed!important;top:0!important;left:0!important;bottom:0!important;z-index:100!important;" +
            "transform:translateX(100%)!important;transition:transform 0.25s ease!important;}" +
            "div[data-testid=\"conversation-panel\"]:not([style*=\"display: none\"]){" +
            "transform:translateX(0)!important;}" +
            "header, header[data-testid=\"sidebar-search\"], " +
            "div[data-testid=\"conversation-header\"]{" +
            "background:#075E54!important;color:white!important;}" +
            "button[data-testid=\"conversation-new\"]," +
            "button[data-testid=\"new-chat-button\"]{" +
            "position:fixed!important;bottom:24px!important;right:24px!important;" +
            "width:56px!important;height:56px!important;border-radius:50%!important;" +
            "background:#00a884!important;box-shadow:0 4px 12px rgba(0,0,0,0.3)!important;z-index:50!important;}" +
            "footer[data-testid=\"conversation-footer\"]{" +
            "background:#1f2c33!important;border-top:1px solid #2a3942!important;}";

        String cssInjection = String.format(
            "(function(){var s=document.createElement('style');s.textContent='%s';" +
            "s.id='wa-wrapper-css';document.head.appendChild(s);})();",
            css
        );
        view.evaluateJavascript(cssInjection, null);

        String layoutFix =
            "window.__WA_wrapper=window.__WA_wrapper||{};" +
            "window.__WA_wrapper.isInChat=function(){" +
            "var p=document.querySelector('[data-testid=\"conversation-panel\"]');" +
            "return p!==null&&p.offsetParent!==null;" +
            "};" +
            "window.__WA_wrapper.goBackToHomepage=function(){" +
            "var b=document.querySelector('[data-testid=\"back\"]," +
            "[role=\"button\"][aria-label*=\"back\" i]," +
            "[aria-label*=\"kembali\" i]');" +
            "if(b){b.click();}" +
            "window.history.back();" +
            "};" +
            "setInterval(function(){" +
            "var s=document.querySelector('[data-testid=\"sidebar\"]');" +
            "var p=document.querySelector('[data-testid=\"conversation-panel\"]');" +
            "if(!s||!p)return;" +
            "s.style.width='100vw';s.style.maxWidth='100vw';s.style.minWidth='100vw';s.style.flex='none';" +
            "p.style.position='fixed';p.style.top='0';p.style.left='0';p.style.bottom='0';p.style.zIndex='100';" +
            "p.style.width='100vw';p.style.maxWidth='100vw';p.style.minWidth='100vw';p.style.flex='none';" +
            "if(p.parentElement)p.parentElement.style.display='block';" +
            "},2000);";

        view.evaluateJavascript(layoutFix, null);
    }
}
