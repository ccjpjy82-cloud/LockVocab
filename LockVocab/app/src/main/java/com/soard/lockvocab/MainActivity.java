package com.soard.lockvocab;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.OutputStream;

public class MainActivity extends Activity {
    ValueCallback<Uri[]> cb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebView wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                if (cb != null) cb.onReceiveValue(null);
                cb = f;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    cb = null;
                    return false;
                }
                return true;
            }
        });
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && cb != null) {
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d));
            cb = null;
        } else {
            super.onActivityResult(r, c, d);
        }
    }

    class Bridge {
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
