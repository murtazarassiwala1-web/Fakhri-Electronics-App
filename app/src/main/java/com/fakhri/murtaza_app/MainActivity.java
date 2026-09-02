package com.fakhri.murtaza_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    public ValueCallback<Uri[]> uploadMessage;
    public static final int FILECHOOSER_RESULTCODE = 100;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

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
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

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
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String js = "javascript:(function() { " +
                        "window.print = function() { window.AndroidBridge.doPrint(); }; " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (uploadMessage == null) return;
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            uploadMessage.onReceiveValue(result);
            uploadMessage = null;
        }
    }

    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void doPrint() {
            runOnUiThread(() -> {
                PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter("Fakhri_Inventory_Doc");
                printManager.print("Fakhri_Print", printAdapter, new PrintAttributes.Builder().build());
            });
        }

        @JavascriptInterface
        public void downloadBase64(String base64Data, String fileName) {
            try {
                String finalFileName = "report.xlsx";
                String base64 = base64Data.substring(base64Data.indexOf(",") + 1);
                byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = mContext.getContentResolver();
                    Uri collection = MediaStore.Files.getContentUri("external");

                    String selection = MediaStore.MediaColumns.DISPLAY_NAME + " == ?";
                    String[] selectionArgs = new String[]{finalFileName};
                    Cursor cursor = resolver.query(collection, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                            Uri existingUri = ContentUris.withAppendedId(collection, id);
                            resolver.delete(existingUri, null, null);
                        }
                        cursor.close();
                    }

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Fakhri Electronics");
                    
                    Uri uri = resolver.insert(collection, values);
                    if (uri != null) {
                        OutputStream os = resolver.openOutputStream(uri);
                        os.write(fileBytes);
                        os.close();
                        runOnUiThread(() -> Toast.makeText(mContext, "Saved in Documents/Fakhri Electronics/report.xlsx!", Toast.LENGTH_LONG).show());
                    }
                } else {
                    File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Fakhri Electronics");
                    if (!path.exists()) {
                        path.mkdirs();
                    }
                    
                    File file = new File(path, finalFileName);
                    if (file.exists()) {
                        file.delete();
                    }
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(fileBytes);
                    os.close();
                    runOnUiThread(() -> Toast.makeText(mContext, "Saved in Documents/Fakhri Electronics/report.xlsx!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(mContext, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
