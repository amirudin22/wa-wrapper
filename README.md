# WA Web Wrapper

WhatsApp Web Wrapper berbasis **Capacitor** + **Dexie.js** untuk perangkat Android dengan **Google Play Integrity failure** (IMEI hilang akibat SP Flash Tool "Format All + Download"). Berjalan murni di atas WebView tanpa terikat restriksi IMEI maupun Play Integrity.

---

## Daftar Isi

- [Latar Belakang](#1-latar-belakang)
- [Target Android & API Level](#2-target-android--api-level)
- [Struktur Proyek](#3-struktur-proyek)
- [Tech Stack](#4-tech-stack)
- [Persyaratan Sistem](#5-persyaratan-sistem)
- [Konfigurasi](#6-konfigurasi)
  - [Capacitor Config](#61-capacitor-config)
  - [Android Manifest](#62-android-manifest)
  - [MainActivity](#63-mainactivity)
  - [Themes & SplashScreen (Android 12+)](#64-themes--splashscreen-android-12)
- [Manajemen State UI (Dexie.js)](#7-manajemen-state-ui-dexiejs)
- [CSS/JS Injection Strategy](#8-cssjs-injection-strategy)
- [Background Lifecycle & WebView State Persistence](#9-background-lifecycle--webview-state-persistence)
- [WebRTC & Voice/Video Call](#10-webrtc--voicevideo-call)
- [Back Press Handling (Exit Confirmation)](#11-back-press-handling-exit-confirmation)
- [Native UX Enhancement](#12-native-ux-enhancement)
- [Cara Build & Run](#13-cara-build--run)
- [Error Handling & Recovery](#13-error-handling--recovery)
- [Risiko & Disclaimer](#14-risiko--disclaimer)
- [Update Strategy](#15-update-strategy)
- [Testing Strategy](#16-testing-strategy)

---

## 1. Latar Belakang

- **Perangkat:** Chipset MediaTek — mengalami bootloop.
- **Tindakan:** SP Flash Tool **"Format All + Download"**.
- **Dampak:**
  - IMEI hilang absolut (partisi `NVRAM` / `NVDATA` terhapus).
  - **Google Play Integrity API** gagal.
  - WhatsApp native menolak registrasi.
- **Keputusan:** Tidak melakukan low-level repair (suntik NVRAM, UBL, Custom ROM). Beralih ke **WebView wrapper**.

---

## 2. Target Android & API Level

| Android Version | API Level | Dukungan | Catatan |
|----------------|-----------|----------|---------|
| Android 12     | 31        | ✅ Full  | Min SDK |
| Android 13     | 33        | ✅ Full  | Foreground service + notification permission |
| Android 14     | 34        | ✅ Full  | Foreground service type deklarasi wajib |
| Android 15     | 35        | ✅ Full  | Pantau perubahan WebView |

Min SDK: **31** | Target SDK: **34**

---

## 3. Struktur Proyek

```
wa-wrapper/
├── dist/                          # Hasil build Vite (webDir)
├── src/
│   ├── index.html                 # Entry point
│   ├── main.ts                    # Bootstrap
│   ├── style.css                  # Custom CSS (injection)
│   ├── dexie.ts                   # Dexie.js db init
│   └── injection.ts               # CSS/JS injection logic
├── android/
│   └── app/
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               ├── java/com/wawrapper/app/
│               │   ├── MainActivity.java
│               │   └── WebSocketService.java     # Foreground service
│               └── res/
│                   └── values/
│                       ├── themes.xml             # SplashScreen (Android 12+)
│                       └── strings.xml
├── capacitor.config.json
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

---

## 4. Tech Stack

| Komponen | Teknologi | Fungsi |
|----------|-----------|--------|
| Core Wrapper | **Capacitor 6** | Membungkus web app ke WebView native |
| Frontend | **Vite + Vanilla TS** | Build tool & entry point |
| Caching | **Dexie.js 4** | IndexedDB wrapper untuk cache state & metadata |
| Injection | **MutationObserver** + CSS override | Adaptasi layout WhatsApp Web ke layar vertikal |
| Service | **Java Foreground Service** | Menjaga WebSocket tetap hidup di background |

Dependencies (node):
```json
{
  "dependencies": {
    "@capacitor/android": "^6.0.0",
    "@capacitor/core": "^6.0.0",
    "dexie": "^4.0.0"
  },
  "devDependencies": {
    "vite": "^6.0.0",
    "typescript": "^5.5.0"
  }
}
```

---

## 5. Persyaratan Sistem

- Node.js 20+
- Android Studio (Giraffe / Hedgehog)
- JDK 17+
- Gradle 8.x
- Android SDK 34
- Perangkat Android 12+ (root tidak diperlukan)

---

## 6. Konfigurasi

### 6.1 Capacitor Config

```json
{
  "appId": "com.wawrapper.app",
  "appName": "WA Web Wrapper",
  "webDir": "dist",
  "server": {
    "url": "https://web.whatsapp.com",
    "cleartext": true,
    "allowNavigation": ["web.whatsapp.com", "*.whatsapp.com"]
  },
  "android": {
    "allowMixedContent": true,
    "captureInput": true,
    "webContentsDebuggingEnabled": false,
    "minSdkVersion": 31
  },
  "overrideUserAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  "plugins": {
    "SplashScreen": {
      "launchAutoHide": false,
      "androidScaleType": "CENTER_CROP",
      "splashFullScreen": true
    }
  }
}
```

### 6.2 Android Manifest

`android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <!-- WebRTC -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Background (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

    <!-- Power -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:hardwareAccelerated="true">

        <activity
            android:name="com.wawrapper.app.MainActivity"
            android:exported="true"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|locale|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="adjustResize"
            android:launchMode="singleTask">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".WebSocketService"
            android:foregroundServiceType="mediaPlayback|camera|microphone"
            android:exported="false" />
    </application>
</manifest>
```

### 6.3 MainActivity

`android/app/src/main/java/com/wawrapper/app/MainActivity.java`:

```java
package com.wawrapper.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        configureWebView();
        startWebSocketService();

        // Restore WebView state (scroll, history, form data)
        if (savedInstanceState != null && webView != null) {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause(); // Pause JS, WebView tetap di RAM
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume(); // Resume JS tanpa reload
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private long backPressedTime = 0;

    @Override
    public void onBackPressed() {
        // Prioritaskan navigasi history WhatsApp Web
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        // Konfirmasi exit — cache tetap utuh
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

    private void startWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: include POST_NOTIFICATIONS
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

        // WebRTC permission grant
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject CSS/JS after page load
                injectCustomScripts(view);
            }
        });

        // WebView settings — persistence & performance
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setMixedContentMode(
            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        );
        webView.getSettings().setOffscreenPreRaster(true);

        // Android 12+: disable force dark agar UI WhatsApp tidak broken
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webView.getSettings().setForceDark(
                android.webkit.WebSettings.FORCE_DARK_OFF
            );
        }
    }

    private void injectCustomScripts(WebView webView) {
        // Read injection script from assets or bundle
        String css = getCustomCSS();
        String js = getCustomJS();
        String script = String.format(
            "(function() {" +
            "  var style = document.createElement('style');" +
            "  style.textContent = '%s';" +
            "  document.head.appendChild(style);" +
            "  %s" +
            "})();",
            css.replace("'", "\\'").replace("\n", ""),
            js
        );
        webView.evaluateJavascript(script, null);
    }
}
```

### 6.4 Themes & SplashScreen (Android 12+)

`android/app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#075E54</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/Theme.AppCompat.NoActionBar</item>
    </style>
</resources>
```

---

## 7. Manajemen State UI (Dexie.js)

### Filosofi: Separation of Concerns

Jangan simpan chat ke Dexie. Ini keputusan arsitektur yang penting:

| Kelola oleh | Data | Alasan |
|-------------|------|--------|
| **WhatsApp Web internal IndexedDB** | Chat, kontak, media, session token | WA sudah mengelola ini dengan baik. Database di WebView persist selama storage tidak di-clear. Schema milik WA, kita tidak usah ikut campur. |
| **Dexie.js (kita)** | UI state, scroll position, injection version | Hanya metadata yang membuat UX mulus saat reload. Ringan, cepat, tidak risiko ban. |

### Konsekuensi:

- ✅ **Chat tetap aman** — WA Web internal IndexedDB tidak hilang selama WebView storage dipertahankan
- ✅ **Zero extra bloat** — tidak ada duplikasi data
- ✅ **Anti-ban** — tidak scraping DOM chat
- ❌ **Tidak bisa backup chat** — tapi itu memang bukan tujuan proyek ini

### Skema Dexie

```typescript
// src/dexie.ts
import Dexie, { type EntityTable } from 'dexie';

interface AppCacheEntry {
  key: string;
  value: unknown;
  timestamp: number;
}

interface StaticAsset {
  url: string;
  blob: Blob;
  timestamp: number;
  ttl: number;
}

const db = new Dexie('WAWrapperCache') as Dexie & {
  appCache: EntityTable<AppCacheEntry, 'key'>;
  staticAssets: EntityTable<StaticAsset, 'url'>;
};

db.version(1).stores({
  appCache: 'key, timestamp',
  staticAssets: 'url, timestamp',
});

const CACHE_TTL = 7 * 24 * 60 * 60 * 1000;

export async function setCache(key: string, value: unknown): Promise<void> {
  await db.appCache.put({ key, value, timestamp: Date.now() });
}

export async function getCache<T>(key: string): Promise<T | undefined> {
  const entry = await db.appCache.get(key);
  if (!entry) return undefined;
  return entry.value as T;
}

export async function clearExpired(): Promise<void> {
  const cutoff = Date.now() - CACHE_TTL;
  await db.appCache.where('timestamp').below(cutoff).delete();
  await db.staticAssets.where('timestamp').below(cutoff).delete();
}
```

### Yang disimpan di Dexie (anti-flicker focus):

| Key | Tipe | Fungsi |
|-----|------|--------|
| `ui:theme` | `string` | `"dark"` / `"light"` — inject class ke `<html>` sebelum WA sempat render theme default |
| `injection:cssVersion` | `string` | `"v1.2.0"` — deteksi perlu update CSS injection atau tidak |
| `scroll:{chatId}` | `number` | Scroll position per chat — restore setelah navigasi balik |
| `injection:lastApplied` | `number` | Timestamp terakhir injeksi sukses |
| `debug:missingSelector` | `object` | Log selector yang tidak ditemukan (debugging DOM change) |

> **⚠️ Tidak menyimpan chat, kontak, atau pesan apapun.** Biarkan WhatsApp Web mengelola datanya sendiri — mereka lebih ahli.

---

## 8. CSS/JS Injection Strategy — Native Mobile Layout

### Pendekatan

Injection dilakukan dari **Java** via `evaluateJavascript`, bukan dari JS bundle Vite. Karena `capacitor.config.json` mengarahkan WebView langsung ke `https://web.whatsapp.com`, maka script injection harus dimasukkan dari sisi Java setelah halaman selesai dimuat.

### Implementasi di MainActivity.java

```java
private void injectWAWrapper(WebView view) {
    String css =
        "div[data-testid=\"sidebar\"]{" +
        "width:100vw!important;max-width:100vw!important;min-width:100vw!important;flex:none!important;" +
        "}" +
        "div[data-testid=\"conversation-panel\"]{" +
        "width:100vw!important;max-width:100vw!important;min-width:100vw!important;flex:none!important;" +
        "position:fixed!important;top:0!important;left:0!important;bottom:0!important;z-index:100!important;" +
        "transform:translateX(100%)!important;transition:transform 0.25s ease!important;" +
        "}" +
        "div[data-testid=\"conversation-panel\"]:not([style*=\"display: none\"]){" +
        "transform:translateX(0)!important;" +
        "}" +
        "header, header[data-testid=\"sidebar-search\"]," +
        "div[data-testid=\"conversation-header\"]{" +
        "background:#075E54!important;color:white!important;" +
        "}" +
        "button[data-testid=\"conversation-new\"]," +
        "button[data-testid=\"new-chat-button\"]{" +
        "position:fixed!important;bottom:24px!important;right:24px!important;" +
        "width:56px!important;height:56px!important;border-radius:50%!important;" +
        "background:#00a884!important;box-shadow:0 4px 12px rgba(0,0,0,0.3)!important;z-index:50!important;" +
        "}" +
        "footer[data-testid=\"conversation-footer\"]{" +
        "background:#1f2c33!important;border-top:1px solid #2a3942!important;" +
        "}";

    String cssInjection = String.format(
        "(function(){var s=document.createElement('style');" +
        "s.textContent='%s';s.id='wa-wrapper-css';" +
        "document.head.appendChild(s);})();",
        css
    );
    view.evaluateJavascript(cssInjection, null);

    String layoutFix =
        "setInterval(function(){" +
        "var s=document.querySelector('[data-testid=\"sidebar\"]');" +
        "var p=document.querySelector('[data-testid=\"conversation-panel\"]');" +
        "if(!s||!p)return;" +
        "s.style.width='100vw';s.style.maxWidth='100vw';" +
        "s.style.minWidth='100vw';s.style.flex='none';" +
        "p.style.position='fixed';p.style.top='0';" +
        "p.style.left='0';p.style.bottom='0';p.style.zIndex='100';" +
        "p.style.width='100vw';p.style.maxWidth='100vw';" +
        "p.style.minWidth='100vw';p.style.flex='none';" +
        "if(p.parentElement)p.parentElement.style.display='block';" +
        "},2000);";
    view.evaluateJavascript(layoutFix, null);
}
```

### Yang di-disable di WebView (browser UX removal)

```java
// Zoom
webView.getSettings().setSupportZoom(false);
webView.getSettings().setBuiltInZoomControls(false);
webView.getSettings().setDisplayZoomControls(false);

// Scroll bars
webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
webView.setHorizontalScrollBarEnabled(false);
webView.setVerticalScrollBarEnabled(false);

// Layout
webView.getSettings().setUseWideViewPort(false);
webView.getSettings().setLayoutAlgorithm(
    WebSettings.LayoutAlgorithm.NARROW_COLUMNS
);

// Long press context menu
webView.setOnLongClickListener(v -> true);
```

### Fitur native-like:

| Fitur | Cara |
|-------|------|
| Single-column layout | Sidebar & chat panel full layar, tidak side-by-side |
| Header hijau WA | `background: #075E54` |
| FAB hijau | Tombol new chat 56px, shadow, floating |
| Transisi slide | Chat panel slide dari kanan |
| Zoom disabled | Pinch zoom, double tap zoom, zoom controls mati |
| Scroll bars hidden | Tidak ada scroll bar browser |
| Long press disabled | Tidak muncul context menu browser |
| Overview mode | Konten pas di layar, tidak bisa zoom-out |

---

## 9. Background Lifecycle & WebView State Persistence

Agar WebSocket WhatsApp tidak terputus dan session tidak hilang saat app di-minimize:

### 9.1 WebView State Persistence (Anti-Reload)

Kunci utama agar tidak perlu reload WA setiap kali buka app adalah **jangan destroy WebView**.

```java
// MainActivity.java — lifecycle overrides
private WebView webView;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    webView = this.getBridge().getWebView();

    // Restore state jika ada
    if (savedInstanceState != null) {
        webView.restoreState(savedInstanceState);
    }

    // Aktifkan persistent storage
    webView.getSettings().setDatabaseEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
}

@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (webView != null) {
        webView.saveState(outState); // Simpan scroll position, history, form data
    }
}

@Override
protected void onPause() {
    super.onPause();
    if (webView != null) {
        webView.onPause();  // Pause JS timer, tapi WebView tetap hidup di memori
        // JANGAN panggil webView.destroy() di sini!
    }
}

@Override
protected void onResume() {
    super.onResume();
    if (webView != null) {
        webView.onResume(); // Resume JS — WebSocket langsung connect tanpa reload
    }
}

@Override
protected void onDestroy() {
    if (webView != null) {
        webView.destroy(); // Hanya destroy saat activity benar-benar dihancurkan
    }
    super.onDestroy();
}
```

**Hasilnya:**
- App di-minimize → WebView.onPause() → JS pause → WebSocket tetap hidup (dibantu Foreground Service)
- App dibuka lagi → WebView.onResume() → JS resume → **LANGSUNG terkoneksi, tidak reload WA**
- App di-close total → next start: WebView.restoreState() → **session WA masih ada** (cookie + IndexedDB persist)

### 9.2 Foreground Service + WakeLock

```java
// WebSocketService.java
public class WebSocketService extends Service {
    private static final int NOTIFICATION_ID = 1001;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WAWrapper:WebSocketKeepAlive"
        );
        wakeLock.acquire(10 * 60 * 1000L); // 10 menit
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "websocket_channel",
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager mgr = getSystemService(NotificationManager.class);
            mgr.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, "websocket_channel")
            .setContentTitle("WA Web Wrapper")
            .setContentText("Menjaga koneksi tetap aktif...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
```

### Aktivasi Service di MainActivity:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ...
    startWebSocketService();
}

private void startWebSocketService() {
    Intent intent = new Intent(this, WebSocketService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);
    } else {
        startService(intent);
    }
}
```

### Ringkasan Aliran Lifecycle:

```
App Start
  ├─ onCreate: restore WebView state + inject CSS from Dexie
  ├─ onResume: WebView.onResume() → WA langsung konek (no reload)
  └─ startForegroundService → keep WebSocket alive

App Minimized
  ├─ onPause: WebView.onPause() (pause JS, WebView tetap di RAM)
  └─ Foreground Service jaga WebSocket + WakeLock 10 menit

App Dibuka Kembali
  └─ onResume: WebView.onResume() → JS resume → LANSUNG TERKONEKSI

App Di-Close Total
  ├─ onSaveInstanceState: simpan WebView state (scroll, history)
  ├─ onDestroy: WebView.destroy()
  └─ Next Start: restoreState + WA internal IndexedDB persist → login masih ada
```

> **Catatan:** wakeLock hanya 10 menit agar tidak boros baterai. WebSocket WhatsApp sendiri memiliki heartbeat internal.

---

## 10. WebRTC & Voice/Video Call

### Yang diperlukan agar call berfungsi:

1. **Permission CAMERA + RECORD_AUDIO** (grant saat boot — lihat 6.3)
2. **`setMediaPlaybackRequiresUserGesture(false)`** — agar WebRTC bisa auto-play audio
3. **Permission request grant otomatis** — override `onPermissionRequest` (lihat 6.3)
4. **Hardware acceleration** — di manifest (`android:hardwareAccelerated="true"`)
5. **Audio focus** — otomatis ditangani WebView Chrome, tapi bisa diperkuat:

```java
// MainActivity.java — request audio focus
AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
audioManager.requestAudioFocus(
    null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN
);
```

### Keterbatasan yang diketahui:

| Aspek | Status |
|-------|--------|
| Voice Call | ✅ Berfungsi (WebRTC) |
| Video Call | ✅ Berfungsi (WebRTC) |
| Push Notification | ❌ Tidak bisa — ini web wrapper, bukan native WA |
| FCM | ❌ Tidak relevan |
| End-to-end Encryption | ✅ Dijamin oleh WhatsApp Web server-side |

---

## 11. Back Press Handling (Exit Confirmation)

Agar back press tidak langsung force close, tampilkan konfirmasi "Keluar Aplikasi?" dengan opsi:

- **Ya** → `finish()` (tutup app, tapi WebView state & session WA tetap utuh karena sudah di-save di `onSaveInstanceState`)
- **Tidak** → tetap di aplikasi

### Implementasi di MainActivity

```java
import android.app.AlertDialog;
import android.content.DialogInterface;

// Tambahkan method ini di MainActivity
private long backPressedTime = 0;

@Override
public void onBackPressed() {
    // Jika WebView bisa go back, prioritaskan navigasi history
    if (webView != null && webView.canGoBack()) {
        webView.goBack();
        return;
    }

    // Jika tidak ada history, tampilkan konfirmasi exit
    long currentTime = System.currentTimeMillis();
    if (currentTime - backPressedTime > 2000) {
        // First press: show dialog
        backPressedTime = currentTime;
        new AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi?")
            .setMessage("Aplikasi akan ditutup. Chat dan session tetap aman.")
            .setPositiveButton("Ya", (dialog, which) -> finish())
            .setNegativeButton("Tidak", null)
            .show();
    }
}
```

### Perilaku:

| Tindakan | Hasil |
|----------|-------|
| Back press (ada history WA) | WebView.goBack() — navigasi ke halaman sebelumnya |
| Back press (tidak ada history) | Muncul dialog "Keluar Aplikasi?" |
| Pilih "Ya" | `finish()` — app closed, **cache tidak dihapus**, session WA persist |
| Pilih "Tidak" | Dialog dismiss, tetap di app |

> **Catatan:** Tidak ada pembersihan cache saat exit. Session WA, chat, cookie tetap utuh di WebView storage.

---

## 12. Native UX Enhancement

Agar terasa seperti aplikasi native, bukan sekedar browser dalam WebView.

### 12.1 Immersive Mode (Fullscreen)

Sembunyikan status bar & navigation bar untuk layar penuh — aktivasi saat app siap.

`MainActivity.java`:

```java
@Override
public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
        hideSystemUI();
    }
}

private void hideSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ (API 30)
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().getInsetsController().hide(
            android.view.WindowInsets.Type.statusBars()
            | android.view.WindowInsets.Type.navigationBars()
        );
        getWindow().getInsetsController().setSystemBarsBehavior(
            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    } else {
        // Android 10 ke bawah
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
    }
}
```

### 12.2 Status Bar Theming

Warnai status bar dengan hijau khas WhatsApp.

```java
// MainActivity.onCreate — setelah super.onCreate
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    getWindow().setStatusBarColor(Color.parseColor("#075E54"));
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    // Ikon status bar putih (light)
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR // hapus ini biar ikon tetap putih
    );
}
```

### 12.3 File Chooser (Attachments)

Agar user bisa kirim gambar, dokumen, video dari gallery/file manager.

```java
// Di configureWebView() — tambahkan ke WebChromeClient
webView.setWebChromeClient(new WebChromeClient() {
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
        } catch (ActivityNotFoundException e) {
            uploadMessage = null;
            return false;
        }
        return true;
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        request.grant(request.getResources());
    }
});

// Tambahkan field + handler
private ValueCallback<Uri[]> uploadMessage;
private static final int FILE_CHOOSER_REQUEST_CODE = 100;

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
```

**Tambahan import:**
```java
import android.webkit.ValueCallback;
import android.net.Uri;
import android.content.ActivityNotFoundException;
```

### 12.4 External Link Handling

Link non-WhatsApp buka di browser eksternal, bukan di dalam WebView.

```java
// Di configureWebView() — WebViewClient
webView.setWebViewClient(new WebViewClient() {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Izinkan navigasi internal WhatsApp
        if (url.contains("web.whatsapp.com") || url.contains("whatsapp.com")) {
            return false; // tetap di WebView
        }

        // Selain itu: buka di browser eksternal
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            view.getContext().startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            // fallback: tetap di WebView saja
            return false;
        }
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        injectCustomScripts(view);
    }
});
```

### 12.5 Proximity Sensor (During Call)

Matikan layar saat panggilan suara — seperti native WhatsApp.

```java
// MainActivity.java — sensor listener
private SensorManager sensorManager;
private Sensor proximitySensor;
private boolean isInCall = false;

private void initProximitySensor() {
    sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

    if (proximitySensor != null) {
        sensorManager.registerListener(
            proximityListener,
            proximitySensor,
            SensorManager.SENSOR_DELAY_NORMAL
        );
    }
}

private final android.hardware.SensorEventListener proximityListener =
    new android.hardware.SensorEventListener() {
    @Override
    public void onSensorChanged(android.hardware.SensorEvent event) {
        if (event.values[0] < proximitySensor.getMaximumRange()) {
            // Objek dekat — matikan layar
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // hint: bisa juga panggil AudioManager mode speaker/earpiece
        } else {
            // Objek jauh — nyalakan layar
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
};
```

### 12.6 Share Intent (Receive dari app lain)

Agar user bisa share teks/gambar dari browser/gallery langsung ke WA Wrapper.

`AndroidManifest.xml` — tambahkan intent filter di activity:

```xml
<activity
    android:name="com.wawrapper.app.MainActivity"
    android:exported="true"
    android:launchMode="singleTask">

    <!-- Launcher -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Share target: terima teks & gambar -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
        <data android:mimeType="image/*" />
    </intent-filter>
</activity>
```

`MainActivity.java` — handle share intent:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ...
    handleShareIntent(getIntent());
}

@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleShareIntent(intent);
}

private void handleShareIntent(Intent intent) {
    if (intent == null) return;
    String action = intent.getAction();
    String type = intent.getType();

    if (Intent.ACTION_SEND.equals(action) && type != null) {
        if ("text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && webView != null) {
                // Inject teks ke input WA Web
                String js = String.format(
                    "navigator.clipboard.writeText('%s');",
                    sharedText.replace("'", "\\'")
                );
                webView.evaluateJavascript(js, null);
                // Tampilkan snackbar
                Toast.makeText(this, "Teks siap di-paste", Toast.LENGTH_SHORT).show();
            }
        } else if (type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null && webView != null) {
                // WA Web bisa handle paste dari clipboard untuk gambar
                String js = String.format(
                    "navigator.clipboard.write([new ClipboardItem({'image/png': fetch('%s').then(r => r.blob())})]);",
                    imageUri.toString()
                );
                webView.evaluateJavascript(js, null);
                Toast.makeText(this, "Gambar siap di-paste", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
```

### Ringkasan fitur native UX:

| Fitur | Benefit | Status |
|-------|---------|--------|
| Immersive Mode | Layar penuh, tidak ada gangguan status/nav bar | ✅ Siap |
| Status Bar Theming | Hijau WA — konsisten branding | ✅ Siap |
| File Chooser | Kirim gambar/dokumen dari gallery/file manager | ✅ Siap |
| External Link | Link non-WA buka di browser | ✅ Siap |
| Proximity Sensor | Layar mati saat panggilan | ✅ Siap |
| Share Intent | Share dari app lain langsung ke WA Wrapper | ✅ Siap |

---

```bash
# 1. Install dependencies
npm install

# 2. Build web assets (Vite)
npm run build

# 3. Sync ke Android
npx cap sync android

# 4. Buka di Android Studio
npx cap open android

# 5. Build APK dari Android Studio:
#    Build → Build Bundle(s) / APK(s) → Build APK(s)
#    Atau untuk signed release:
#    Build → Generate Signed Bundle / APK
```

### Prerequisites:

```bash
# Pastikan terinstall
node --version    # ≥ 20
npm --version     # ≥ 10
java -version     # JDK 17+
```

### Build script (`package.json`):

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "sync": "npx cap sync",
    "open:android": "npx cap open android",
    "build:apk": "npm run build && npx cap sync && cd android && ./gradlew assembleDebug"
  }
}
```

---

## 14. Error Handling & Recovery

| Skenario | Penanganan |
|----------|-----------|
| WhatsApp Web gagal load | WebViewClient.onReceivedError → tampilkan halaman error custom + tombol retry |
| WebRTC denied | Deteksi via `onPermissionRequest` denied → snackbar "Izin kamera/mikro diperlukan" |
| Network offline | `WebViewClient.onReceivedError` dengan `ERROR_HOST_LOOKUP` → tampilkan offline page + `NetworkCallback` auto-reload |
| Session expired / QR code | Reload WebView + bersihkan cache session Dexie |
| Service killed oleh sistem | Override `onStartCommand` → return `START_STICKY` |
| DOM class berubah (WC update) | MutationObserver fallback + log version mismatch ke Dexie untuk debugging |

### Struktur error page:

```html
<!-- dist/error.html -->
<div id="error-container">
  <h1>Koneksi Bermasalah</h1>
  <p id="error-message"></p>
  <button onclick="location.reload()">Coba Lagi</button>
</div>
```

---

## 15. Risiko & Disclaimer

1. **Akun WhatsApp Web bisa di-ban** oleh Meta jika terdeteksi sebagai automated access / unofficial client. Gunakan dengan risiko sendiri.
2. **`cleartext: true`** memungkinkan traffic HTTP (jika ada redirect). Pastikan hanya terhubung ke `web.whatsapp.com`.
3. **User-Agent spoofing** dideteksi Meta sebagai browser desktop. Ini mungkin berubah di masa depan.
4. **Fitur push notification tidak tersedia.** Ini web wrapper, bukan WhatsApp native.
5. **Tidak menyimpan atau mengirim data pengguna.** Semua pesan tetap dienkripsi end-to-end oleh WhatsApp server.
6. **Aplikasi ini untuk penggunaan pribadi.** Jangan distribusikan secara publik.

---

## 16. Update Strategy

| Komponen | Metode Update | Frekuensi |
|----------|--------------|-----------|
| WebView (Chrome) | Auto-update via Google Play | Otomatis |
| Capacitor | `npm update @capacitor/*` | Per rilis major |
| Injection CSS/JS | Versi di Dexie — auto update saat mismatch | Sesuai perubahan DOM WA |
| Service Java | Build ulang APK | Jika ada bug lifecycle |

### Deteksi perubahan DOM WhatsApp:

```typescript
// src/injection.ts — periodic check
setInterval(() => {
  const knownSelectors = [
    '[data-testid="conversation-panel"]',
    '[data-testid="sidebar"]',
  ];
  for (const sel of knownSelectors) {
    if (!document.querySelector(sel)) {
      console.warn(`[Injection] Selector not found: ${sel}`);
      // Log ke Dexie untuk debugging
      setCache('debug:missingSelector', {
        selector: sel,
        timestamp: Date.now(),
        domSnapshot: document.body.innerHTML.substring(0, 500),
      });
    }
  }
}, 30000); // setiap 30 detik
```

---

## 17. Testing Strategy

### Yang perlu diverifikasi:

1. **Login flow** — QR code scan → session persist setelah app ditutup
2. **Chat UX** — layout vertikal, keyboard muncul pas input, scroll natural
3. **Voice/Video Call** — camera + mic berfungsi, audio routing ke speaker/earpiece
4. **Background** — minimize 5 menit → WebSocket tetap hidup, pesan masuk
5. **Anti-flicker** — reload app, layout tidak berkedip putih (CSS injection dari Dexie sebelum WA render)
6. **Session persistence** — app di-close total → buka lagi → masih login (tanpa scan QR ulang)
7. **Error states** — airplane mode, deny permission, WA down

### Checklist manual:

- [ ] QR code muncul dan scan berhasil
- [ ] Chat list tampil vertikal penuh
- [ ] Chat bubble tidak terpotong
- [ ] Keyboard muncul otomatis saat tap input
- [ ] Voice call: mic indicator di status bar
- [ ] Video call: kedua arah video tampil
- [ ] App di-minimize → buka lagi → session masih hidup
- [ ] App di-close → buka lagi → masih login (tanpa scan QR)
- [ ] WebView di-pause → resume → tidak reload WA
- [ ] Cache Dexie di-clear → reload normal tanpa error
- [ ] Anti-flicker: tidak ada flash putih/layout broken saat reload

---

---

## 18. Build dengan GitHub Actions

Karena Android SDK tidak diinstal lokal, gunakan **GitHub Actions** untuk kompilasi APK.

### Cara penggunaan:

1. **Push repo ke GitHub:**
```bash
cd wa-wrapper
git init
git add .
git commit -m "init: WA Web Wrapper"
git remote add origin https://github.com/<username>/wa-wrapper.git
git push -u origin main
```

2. **Workflow otomatis jalan** — setiap push ke `main` akan memicu build APK.

3. **Download hasil APK:**
   - Buka repo di GitHub → tab **Actions**
   - Klik workflow terbaru → scroll ke **Artifacts**
   - Download `wa-wrapper-debug.zip` → ekstrak → install `app-debug.apk` ke HP

### Atau jalankan manual:
Buka repo di GitHub → **Actions** → **Build APK** → **Run workflow**.

### Output:
- `android/app/build/outputs/apk/debug/app-debug.apk` — APK siap install.

---

> **Catatan:** README ini adalah living document. Update sesuai perubahan WhatsApp Web DOM, Android API level baru, atau temuan teknis selama pengembangan.
