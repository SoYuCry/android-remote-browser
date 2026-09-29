package io.github.soyucy.androidremote.companion;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Real-device HTTP and credential regression tests, without starting Feishu or changing user settings. */
public final class QuickChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            SharedPreferences prefs = getTargetContext().getSharedPreferences("isolated_pairing_test", 0);
            prefs.edit().clear().commit();
            PairingStore store = new PairingStore(prefs);
            require(store.pair("000000") == null, "pairing must be locally enabled");
            String code = store.createCode();
            String token = store.pair(code);
            require(token != null && store.authorized("Bearer " + token), "valid pairing");
            require(store.pair(code) == null, "one-use pairing code");
            require(!store.authorized("Bearer invalid"), "invalid credentials rejected");
            require(new PairingStore(prefs).authorized("Bearer " + token), "credential survives restart");
            String second = store.pair(store.createCode());
            require(store.revoke("Bearer " + token), "revoke current device");
            require(!store.authorized("Bearer " + token) && store.authorized("Bearer " + second), "other devices stay paired");
            store.revokeAll();
            require(!store.authorized("Bearer " + second), "revocation");
            code = store.createCode();
            for (int i = 0; i < 5; i++) store.pair("not-a-code");
            require(store.pair(code) == null, "pairing attempt limit");
            prefs.edit().clear().commit();
            try (ProxyServer server = new ProxyServer(getTargetContext(), 0, "127.0.0.1", 5900)) {
                server.start();
                String base = "http://127.0.0.1:" + server.listeningPort();
                check(base + "/api/status", null, 403);
                check(base + "/api/status", "1", 401);
                HttpURLConnection cross = connection(base + "/api/pair", "1");
                cross.setRequestProperty("Origin", "http://evil.example");
                require(cross.getResponseCode() == 403, "cross-origin pairing rejected"); cross.disconnect();
                check(base + "/api/pair", "1", 405);
                HttpURLConnection quick = connection(base + "/quick", null);
                require(quick.getResponseCode() == 200, "quick page served");
                require("no-store".equals(quick.getHeaderField("Cache-Control")), "quick page is fresh");
                String page = read(quick.getInputStream());
                require(!page.contains("ui.bundle.js"), "quick page does not load noVNC"); quick.disconnect();
                HttpURLConnection html = connection(base + "/vnc.html", null);
                String vnc = read(html.getInputStream()); html.disconnect();
                java.util.regex.Matcher script = java.util.regex.Pattern.compile("src=\"([^\"]+ui.bundle.js)\"").matcher(vnc);
                require(script.find(), "bundled VNC script included");
                HttpURLConnection js = connection(base + script.group(1), null);
                require(js.getResponseCode() == 200 && js.getHeaderField("Cache-Control").contains("immutable"), "versioned asset cache"); js.disconnect();
                HttpURLConnection gzip = connection(base + script.group(1), null);
                gzip.setRequestProperty("Accept-Encoding", "gzip");
                require("gzip".equals(gzip.getHeaderField("Content-Encoding")), "gzip response");
                require(read(new java.util.zip.GZIPInputStream(gzip.getInputStream())).length() > 1000, "gzip stream decodes"); gzip.disconnect();
                HttpURLConnection plain = connection(base + script.group(1), null);
                plain.setRequestProperty("Accept-Encoding", "gzip;q=0");
                require(plain.getHeaderField("Content-Encoding") == null, "gzip opt-out respected"); plain.disconnect();
                check(base + "/static/invalid/app/ui.bundle.js", null, 404);
            }
            result.putString("stream", "PASS: pairing, persistence, revocation, attempt limits, API authorization, CSRF, quick page, bundle, versioned cache\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static HttpURLConnection connection(String url, String client) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(3000);
        if (client != null) c.setRequestProperty("X-Remote-Client", client);
        return c;
    }
    private static void check(String url, String client, int expected) throws Exception {
        HttpURLConnection c = connection(url, client);
        try { require(c.getResponseCode() == expected, url + " expected " + expected); } finally { c.disconnect(); }
    }
    private static String read(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
