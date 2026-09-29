package io.github.soyucy.androidremote.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Only hashes of browser credentials are stored; pairing is enabled locally for five minutes. */
final class PairingStore {
    private static PairingStore instance;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private String code = "";
    private long expires;
    private int attempts;

    static synchronized PairingStore get(Context context) {
        if (instance == null) instance = new PairingStore(context);
        return instance;
    }

    private PairingStore(Context context) {
        this(context.createDeviceProtectedStorageContext().getSharedPreferences("quick_pairing", Context.MODE_PRIVATE));
    }

    PairingStore(SharedPreferences prefs) { this.prefs = prefs; }

    synchronized String createCode() {
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        expires = SystemClock.elapsedRealtime() + 300_000;
        attempts = 0;
        return code;
    }

    synchronized String pair(String candidate) {
        if (code.isEmpty() || SystemClock.elapsedRealtime() >= expires || attempts >= 5) return null;
        attempts++;
        if (!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8))) return null;
        Set<String> hashes = new HashSet<>(prefs.getStringSet("tokens", new HashSet<>()));
        if (hashes.size() >= 8) return null;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        hashes.add(hash(token));
        if (!prefs.edit().putStringSet("tokens", hashes).commit()) return null;
        code = "";
        return token;
    }

    synchronized boolean authorized(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        String token = authorization.substring(7);
        return token.length() == 43 && prefs.getStringSet("tokens", new HashSet<>()).contains(hash(token));
    }

    synchronized boolean revokeAll() {
        code = "";
        return prefs.edit().remove("tokens").commit();
    }

    synchronized boolean revoke(String authorization) {
        if (!authorized(authorization)) return false;
        Set<String> hashes = new HashSet<>(prefs.getStringSet("tokens", new HashSet<>()));
        hashes.remove(hash(authorization.substring(7)));
        return prefs.edit().putStringSet("tokens", hashes).commit();
    }

    synchronized int count() { return prefs.getStringSet("tokens", new HashSet<>()).size(); }

    private static String hash(String token) {
        try {
            return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
