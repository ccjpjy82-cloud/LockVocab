package com.soard.lockvocab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    WebView wv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("file:///android_asset/index.html");
    }

    void js(final String code) {
        runOnUiThread(() -> wv.evaluateJavascript(code, null));
    }

    static Bitmap decode(ContentResolver cr, Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(u)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = cr.openInputStream(u)) {
            bm = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bm == null) throw new Exception("읽을 수 없는 사진");
        int rot = 0;
        try (InputStream in = cr.openInputStream(u)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            if (ori == 6) rot = 90;
            else if (ori == 3) rot = 180;
            else if (ori == 8) rot = 270;
        } catch (Exception e) { }
        float sc = Math.min(1f, 800f / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix();
        m.postScale(sc, sc);
        m.postRotate(rot);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r != 2) {
            super.onActivityResult(r, c, d);
            return;
        }
        final ArrayList<Uri> uris = new ArrayList<>();
        if (c == RESULT_OK && d != null) {
            ClipData cd = d.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (d.getData() != null) {
                uris.add(d.getData());
            }
        }
        if (uris.isEmpty()) {
            js("window.onNativeMsg('선택된 사진이 없어요')");
            return;
        }
        js("window.onNativeMsg('사진 " + uris.size() + "장 처리 중...')");
        new Thread(() -> {
            int n = 0;
            for (Uri u : uris) {
                try {
                    Bitmap bm = decode(getContentResolver(), u);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG, 75, bo);
                    String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                    js("window.onNativeImage('data:image/jpeg;base64," + b64 + "')");
                    n++;
                } catch (Exception e) {
                    String msg = String.valueOf(e.getMessage()).replace("'", " ").replace("\n", " ");
                    js("window.onNativeMsg('실패: " + msg + "')");
                }
            }
            if (n > 0) js("window.onNativeMsg('" + n + "장 넣었어요')");
        }).start();
    }

    class Bridge {
        @JavascriptInterface
        public void pickImages() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, 2);
                } catch (Exception e) {
                    js("window.onNativeMsg('갤러리를 열 수 없어요')");
                }
            });
        }

        @JavascriptInterface
        public void setWords(String json) {
            getSharedPreferences("w", 0).edit().putString("words", json).apply();
            WordWidget.refresh(MainActivity.this);
        }

        @JavascriptInterface
        public boolean save(String name, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LockVocab");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    o.write(data);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
