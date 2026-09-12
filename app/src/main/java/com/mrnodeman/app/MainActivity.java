package com.mrnodeman.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private final static int PERMISSION_REQUEST_CODE = 100;
    private volatile boolean isIncognitoActive = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hardware Acceleration Flag for maximum animation/scroll performance
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        setContentView(R.layout.activity_main);

        // Enable edge-to-edge transparent status bar and navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            window.getDecorView().setSystemUiVisibility(uiOptions);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        // Auto-detect and request maximum display refresh rate (60Hz, 90Hz, 120Hz, 144Hz, 165Hz)
        enableHighRefreshRate();

        webView = findViewById(R.id.webView);
        webView.setSoundEffectsEnabled(false);
        webView.setHapticFeedbackEnabled(false);

        // Enable Hardware Accelerated GPU rendering layer & disable overscroll shadow jitter
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Optimized WebSettings for high FPS JS rendering & permanent data persistence
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        try {
            String dbPath = getApplicationContext().getDir("databases", Context.MODE_PRIVATE).getPath();
            webSettings.setDatabasePath(dbPath);
        } catch (Exception ignored) {}
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setEnableSmoothTransition(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webSettings.setOffscreenPreRaster(true);
        }
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        // Handle URL loading: launch external links in device browser/app intent
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:") || url.startsWith("tel:"))) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                view.loadUrl(url);
                return true;
            }
        });

        // Setup File Chooser & Permission Security for Web App
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (isIncognitoActive) {
                    // Strictly deny any WebRTC, microphone, camera, or media capture requests
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                request.deny();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    super.onPermissionRequest(request);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                if (isIncognitoActive) {
                    callback.invoke(origin, false, false);
                    return;
                }
                super.onGeolocationPermissionsShowPrompt(origin, callback);
            }
        });

        // Register custom JavaScript interface for exporting files and system integrations
        webView.addJavascriptInterface(new AndroidInterface(), "AndroidApp");

        // Load the local HTML file from assets
        webView.loadUrl("file:///android_asset/index.html");

        // Request runtime permissions (vibrate and notifications)
        requestRuntimePermissions();
    }

    // Handles result from native file chooser and returns file to WebView
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (uploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // Intercepts the back button and passes to JavaScript for custom app navigation
    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.loadUrl("javascript:window.handleSystemBack()");
        } else {
            super.onBackPressed();
        }
    }

    // Native Interface exposing operations to JavaScript
    public class AndroidInterface {
        
        @JavascriptInterface
        public void exportFile(final String filename, final String base64Data, final String mimeType) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                        
                        // 1. Try to save directly to the device's Downloads directory (Android 10+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                            ContentResolver resolver = getContentResolver();
                            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (uri != null) {
                                try (OutputStream os = resolver.openOutputStream(uri)) {
                                    if (os != null) {
                                        os.write(bytes);
                                        Toast.makeText(MainActivity.this, "File saved to Downloads: " + filename, Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }
                            }
                        } else {
                            // Direct File writing fallback for older Android versions
                            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            if (!dir.exists()) {
                                dir.mkdirs();
                            }
                            File file = new File(dir, filename);
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(bytes);
                                Toast.makeText(MainActivity.this, "File saved to Downloads: " + filename, Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // 2. Fallback: Trigger native share intent if Direct Downloads folder save fails
                    triggerShareIntent(filename, base64Data, mimeType);
                }
            });
        }

        @JavascriptInterface
        public void openExternalUrl(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Could not open link: " + url, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void setScreenCaptureProtection(final boolean enable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        isIncognitoActive = enable;
                        if (enable) {
                            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                            if (webView != null) {
                                WebSettings ws = webView.getSettings();
                                ws.setGeolocationEnabled(false);
                                ws.setMediaPlaybackRequiresUserGesture(true);
                            }
                        } else {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                            if (webView != null) {
                                WebSettings ws = webView.getSettings();
                                ws.setGeolocationEnabled(false);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isScreenCaptureProtected() {
            try {
                return (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean isHardwarePrivacyActive() {
            return isIncognitoActive;
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        finishAndRemoveTask();
                    } else {
                        finish();
                    }
                }
            });
        }

        // ── Native Persistent Flash Disk Key-Value Storage ──
        @JavascriptInterface
        public boolean saveData(final String key, final String value) {
            if (key == null) return false;
            try {
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                boolean committed = prefs.edit().putString(key, value).commit();
                
                // Secondary file mirror backup for maximum persistence safety
                try {
                    File storeDir = new File(getFilesDir(), "mrnodeman_data");
                    if (!storeDir.exists()) storeDir.mkdirs();
                    File file = new File(storeDir, "store_" + Math.abs(key.hashCode()) + ".dat");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        if (value != null) {
                            fos.write(value.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception ignored) {}
                
                return committed;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @JavascriptInterface
        public String getData(final String key) {
            if (key == null) return null;
            try {
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                String val = prefs.getString(key, null);
                if (val != null) return val;

                // Fallback to secondary file mirror backup
                File file = new File(new File(getFilesDir(), "mrnodeman_data"), "store_" + Math.abs(key.hashCode()) + ".dat");
                if (file.exists() && file.length() > 0) {
                    try (FileInputStream fis = new FileInputStream(file);
                         InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                         BufferedReader br = new BufferedReader(isr)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        String content = sb.toString().trim();
                        // Restore in SharedPreferences
                        prefs.edit().putString(key, content).apply();
                        return content;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @JavascriptInterface
        public boolean removeData(final String key) {
            if (key == null) return false;
            try {
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                prefs.edit().remove(key).commit();
                File file = new File(new File(getFilesDir(), "mrnodeman_data"), "store_" + Math.abs(key.hashCode()) + ".dat");
                if (file.exists()) file.delete();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @JavascriptInterface
        public String getAllData() {
            try {
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                Map<String, ?> all = prefs.getAll();
                JSONObject json = new JSONObject(all);
                return json.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "{}";
            }
        }

        // ── Native Master Database Snapshot Persistence ──
        @JavascriptInterface
        public boolean saveAppDatabase(final String jsonContent) {
            if (jsonContent == null || jsonContent.isEmpty()) return false;
            try {
                File dbFile = new File(getFilesDir(), "mrnodeman_master_db.json");
                try (FileOutputStream fos = new FileOutputStream(dbFile)) {
                    fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }
                // Also mirror into SharedPreferences
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                prefs.edit().putString("_mnm_master_db_backup", jsonContent).commit();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @JavascriptInterface
        public String loadAppDatabase() {
            try {
                File dbFile = new File(getFilesDir(), "mrnodeman_master_db.json");
                if (dbFile.exists() && dbFile.length() > 0) {
                    try (FileInputStream fis = new FileInputStream(dbFile);
                         InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                         BufferedReader br = new BufferedReader(isr)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        String res = sb.toString().trim();
                        if (!res.isEmpty()) return res;
                    }
                }
                // Fallback to SharedPreferences
                SharedPreferences prefs = getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                return prefs.getString("_mnm_master_db_backup", null);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // Helper method to present a Share sheet fallback for exports
    private void triggerShareIntent(String filename, String base64Data, String mimeType) {
        try {
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            File cacheDir = new File(getCacheDir(), "shared_files");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File file = new File(cacheDir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(intent, "Save or Export File"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to export file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Queries display capabilities and requests highest refresh rate mode (60Hz, 90Hz, 120Hz, 144Hz, 165Hz)
    private void enableHighRefreshRate() {
        try {
            Window window = getWindow();
            WindowManager.LayoutParams lp = window.getAttributes();
            android.view.Display display = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display = getDisplay();
            }
            if (display == null) {
                display = getWindowManager().getDefaultDisplay();
            }
            if (display != null) {
                float maxRefreshRate = 60.0f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.view.Display.Mode[] modes = display.getSupportedModes();
                    android.view.Display.Mode highestMode = null;
                    if (modes != null) {
                        for (android.view.Display.Mode mode : modes) {
                            if (mode.getRefreshRate() > maxRefreshRate) {
                                maxRefreshRate = mode.getRefreshRate();
                                highestMode = mode;
                            }
                        }
                    }
                    if (highestMode != null) {
                        lp.preferredDisplayModeId = highestMode.getModeId();
                    }
                    lp.preferredRefreshRate = maxRefreshRate;
                }
                // Set preferred min/max display refresh rates if supported by Android platform
                try {
                    java.lang.reflect.Field minField = WindowManager.LayoutParams.class.getField("preferredMinDisplayRefreshRate");
                    minField.setFloat(lp, maxRefreshRate);
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field maxField = WindowManager.LayoutParams.class.getField("preferredMaxDisplayRefreshRate");
                    maxField.setFloat(lp, maxRefreshRate);
                } catch (Throwable ignored) {}

                window.setAttributes(lp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableHighRefreshRate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableHighRefreshRate();
        }
    }

    // Requests dynamic notification permissions on Android 13+
    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
