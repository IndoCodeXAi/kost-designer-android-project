package com.kostdesigner.app;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Menyimpan library eksternal (Tailwind CDN, Three.js, OrbitControls, Google Fonts)
 * ke penyimpanan internal aplikasi begitu berhasil dimuat sekali lewat internet.
 * Setelah itu, setiap kali aplikasi dibuka lagi -- online ataupun offline --
 * file yang sama akan dibaca langsung dari penyimpanan lokal, bukan dari internet.
 *
 * Catatan: HTML, CSS, JS buatan sendiri (assets/index.html) sudah otomatis
 * offline karena memang dibundel di dalam APK -- ini khusus untuk library
 * pihak ketiga yang di-load lewat <script src="https://...">.
 */
public class OfflineCachingWebViewClient extends WebViewClient {

    private static final String TAG = "OfflineCache";

    private static final String[] CACHEABLE_HOSTS = {
            "cdn.tailwindcss.com",
            "cdnjs.cloudflare.com",
            "cdn.jsdelivr.net",
            "fonts.googleapis.com",
            "fonts.gstatic.com"
    };

    private final File cacheDir;

    OfflineCachingWebViewClient(File persistentStorageDir) {
        this.cacheDir = new File(persistentStorageDir, "offline_libs");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    private boolean isCacheable(String url) {
        if (url == null) return false;
        for (String host : CACHEABLE_HOSTS) {
            if (url.contains(host)) return true;
        }
        return false;
    }

    private File fileForUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return new File(cacheDir, sb.toString());
        } catch (Exception e) {
            return new File(cacheDir, String.valueOf(url.hashCode()));
        }
    }

    private String guessMimeType(String url) {
        if (url.contains(".css")) return "text/css";
        if (url.contains(".woff2")) return "font/woff2";
        if (url.contains(".woff")) return "font/woff";
        if (url.contains(".ttf")) return "font/ttf";
        // Tailwind/Three.js/OrbitControls semuanya JavaScript
        return "application/javascript";
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        if (!isCacheable(url)) {
            return super.shouldInterceptRequest(view, request);
        }

        File cached = fileForUrl(url);

        // 1) Sudah pernah diunduh sebelumnya -> langsung pakai salinan lokal (aman walau offline)
        if (cached.exists() && cached.length() > 0) {
            try {
                return new WebResourceResponse(guessMimeType(url), "UTF-8", new FileInputStream(cached));
            } catch (IOException ignored) {
                // lanjut coba unduh ulang di bawah
            }
        }

        // 2) Belum ada salinan lokal -> coba unduh sekali lewat internet, sekaligus simpan untuk offline nanti
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            File tmp = new File(cacheDir, cached.getName() + ".tmp");
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.close();
            in.close();
            tmp.renameTo(cached);

            return new WebResourceResponse(guessMimeType(url), "UTF-8", new FileInputStream(cached));
        } catch (IOException e) {
            Log.w(TAG, "Tidak ada internet & belum ada cache untuk: " + url);
            // Tidak ada internet sama sekali untuk file ini -> biarkan WebView coba sendiri (akan gagal jika memang offline)
            return super.shouldInterceptRequest(view, request);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
