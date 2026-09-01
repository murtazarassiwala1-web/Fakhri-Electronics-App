package com.fakhri.murtaza_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        // 1. Camera aur Storage ki direct permissions
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

        // 2. Camera Permission ko WebView me bypass karna
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        // Java ko JavaScript se jodna
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 3. Print aur Excel (Blob) Exports ko HTML se intercept karke Android ko bhejna
                String js = "javascript:(function() { " +
                        "window.print = function() { AndroidBridge.doPrint(); }; " +
                        "document.addEventListener('click', function(e) { " +
                        "  var target = e.target.closest('a'); " +
                        "  if (target && target.href && target.href.startsWith('blob:')) { " +
                        "    e.preventDefault(); " +
                        "    var xhr = new XMLHttpRequest(); " +
                        "    xhr.open('GET', target.href, true); " +
                        "    xhr.responseType = 'blob'; " +
                        "    xhr.onload = function() { " +
                        "      var reader = new FileReader(); " +
                        "      reader.readAsDataURL(xhr.response); " +
                        "      reader.onloadend = function() { " +
                        "        AndroidBridge.downloadBase64(reader.result, target.download || 'Fakhri_Export.xlsx'); " +
                        "      } " +
                        "    }; " +
                        "    xhr.send(); " +
                        "  } " +
                        "}); " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    // Yeh class JavaScript se data receive karti hai
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
                // Base64 text ko wapas File me convert karke Downloads folder me save karna
                String base64 = base64Data.substring(base64Data.indexOf(",") + 1);
                byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                
                File downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsPath, fileName);
                
                FileOutputStream os = new FileOutputStream(file);
                os.write(fileBytes);
                os.close();
                
                runOnUiThread(() -> Toast.makeText(mContext, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(mContext, "Download Failed!", Toast.LENGTH_SHORT).show());
            }
        }
    }
}
function importData(event) {
    const file = event.target.files[0]; 
    if (!file) return;
    
    const reader = new FileReader();
    reader.onload = function(e) {
        try {
            const data = new Uint8Array(e.target.result);
            const workbook = XLSX.read(data, {type: 'array'});
            
            // Restore Inventory Data
            if(workbook.SheetNames.includes("Inventory")) {
                const invSheet = workbook.Sheets["Inventory"];
                const invJson = XLSX.utils.sheet_to_json(invSheet);
                
                inventory = invJson.map(row => ({
                    id: String(row["ID"]),
                    category: row["Category"] || "Other",
                    name: String(row["Name"]),
                    price: parseFloat(row["Price"]) || 0,
                    stock: parseInt(row["Stock"]) || 0
                })).filter(item => item.id && item.name !== "undefined");
            }
            
            // Restore Revenue Data
            if(workbook.SheetNames.includes("Daily Revenue")) {
                const revSheet = workbook.Sheets["Daily Revenue"];
                const revJson = XLSX.utils.sheet_to_json(revSheet);
                
                revenueData = {};
                revJson.forEach(row => {
                    if(row["Date"] && row["Sales"]) {
                        revenueData[row["Date"]] = parseFloat(row["Sales"]);
                    }
                });
            }
            
            saveDB(); 
            alert("Excel Backup Restored Successfully!"); 
            loadProducts();
            
            // Reset the input so you can upload the same file again if needed
            event.target.value = ''; 
        } catch(err) {
            alert("Error reading Excel file! Make sure it's the exact Fakhri_Report.xlsx format.");
            console.error(err);
        }
    }; 
    // Read as ArrayBuffer for Excel files instead of Text
    reader.readAsArrayBuffer(file);
}
