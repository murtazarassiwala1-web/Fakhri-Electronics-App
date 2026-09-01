package com.fakhri.inventory;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        // Permissions mangna (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            }, 100);
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        // 1. Camera Fix
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        // 2. Print Fix (JavaScript ko Android Printer se jodna)
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void doPrint() {
                runOnUiThread(() -> {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter("Fakhri_Inventory_QR");
                    printManager.print("Fakhri_Document", printAdapter, null);
                });
            }
        }, "AndroidPrinter");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // HTML ke window.print() ko override karke Android Printer par bhejna
                view.evaluateJavascript("window.print = function() { AndroidPrinter.doPrint(); };", null);
            }
        });

        // 3. Export Fix (Base64/Data URI Files ko phone me save karna)
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("data:")) {
                try {
                    String base64 = url.substring(url.indexOf(",") + 1);
                    byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(path, "Fakhri_Export_" + System.currentTimeMillis() + ".xlsx");
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(fileBytes);
                    os.close();
                    Toast.makeText(getApplicationContext(), "File Downloaded in Downloads folder", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "Download Failed", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Direct URL Downloads not supported offline", Toast.LENGTH_SHORT).show();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }
}
