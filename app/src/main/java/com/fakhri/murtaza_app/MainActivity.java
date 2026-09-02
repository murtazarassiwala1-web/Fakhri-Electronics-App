        @JavascriptInterface
        public void downloadBase64(String base64Data, String fileName) {
            try {
                // फ़ाइल का नाम "report.xlsx" फिक्स कर दिया है
                String finalFileName = "report.xlsx";
                
                String base64 = base64Data.substring(base64Data.indexOf(",") + 1);
                byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = mContext.getContentResolver();
                    // Documents में सेव करने के लिए MediaStore.Files का इस्तेमाल
                    Uri collection = MediaStore.Files.getContentUri("external");

                    // 1. पुरानी report.xlsx ढूंढो और डिलीट करो (Overwrite)
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

                    // 2. नई फाइल Documents/Fakhri Electronics में सेव करो
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    // Path सेट किया गया: Documents > Fakhri Electronics
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Fakhri Electronics");
                    
                    Uri uri = resolver.insert(collection, values);
                    if (uri != null) {
                        OutputStream os = resolver.openOutputStream(uri);
                        os.write(fileBytes);
                        os.close();
                        runOnUiThread(() -> Toast.makeText(mContext, "Saved in Documents/Fakhri Electronics/report.xlsx!", Toast.LENGTH_LONG).show());
                    }
                } else {
                    // पुराने Android वर्ज़न के लिए (Android 9 या उससे नीचे)
                    File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Fakhri Electronics");
                    if (!path.exists()) {
                        path.mkdirs(); // अगर फोल्डर नहीं है तो नया बना देगा
                    }
                    
                    File file = new File(path, finalFileName);
                    if (file.exists()) {
                        file.delete(); // पुरानी फाइल डिलीट
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
                String base64 = base64Data.substring(base64Data.indexOf(",") + 1);
                byte[] fileBytes = Base64.decode(base64, Base64.DEFAULT);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = mContext.getContentResolver();
                    Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

                    // 1. Purani file dhoondho aur delete karo (Overwrite logic)
                    String selection = MediaStore.Downloads.DISPLAY_NAME + " == ?";
                    String[] selectionArgs = new String[]{fileName};
                    Cursor cursor = resolver.query(collection, new String[]{MediaStore.Downloads._ID}, selection, selectionArgs, null);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                            Uri existingUri = ContentUris.withAppendedId(collection, id);
                            resolver.delete(existingUri, null, null);
                        }
                        cursor.close();
                    }

                    // 2. Nayi file fresh save karo
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    
                    Uri uri = resolver.insert(collection, values);
                    if (uri != null) {
                        OutputStream os = resolver.openOutputStream(uri);
                        os.write(fileBytes);
                        os.close();
                        runOnUiThread(() -> Toast.makeText(mContext, "Excel Exported & Overwritten!", Toast.LENGTH_LONG).show());
                    }
                } else {
                    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(path, fileName);
                    // Purani file delete (older Android)
                    if (file.exists()) {
                        file.delete();
                    }
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(fileBytes);
                    os.close();
                    runOnUiThread(() -> Toast.makeText(mContext, "Excel Exported & Overwritten!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(mContext, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
