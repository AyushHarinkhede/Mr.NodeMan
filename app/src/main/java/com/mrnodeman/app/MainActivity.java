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
import android.webkit.JavascriptInterface;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private final static int PERMISSION_REQUEST_CODE = 100;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Customize status bar color for beautiful visual appearance matching web theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#07070A"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Ensure text remains readable if theme background changes
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }

        webView = findViewById(R.id.webView);
        webView.setSoundEffectsEnabled(false);
        webView.setHapticFeedbackEnabled(false);

        // Standard web settings optimized for modern JavaScript apps
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // Prevent opening urls in default phone browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        // Setup File Chooser for Web App input fields
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
        public void exitApp() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.super.onBackPressed();
                }
            });
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
