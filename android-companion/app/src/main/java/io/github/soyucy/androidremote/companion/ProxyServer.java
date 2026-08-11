package io.github.soyucy.androidremote.companion;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProxyServer implements Closeable {
    static final int DEFAULT_HTTP_PORT = 6080;
    static final String DEFAULT_VNC_HOST = "127.0.0.1";
    static final int DEFAULT_VNC_PORT = 5900;

    private static final String TAG = "RemoteProxyServer";
    private static final String ASSET_ROOT = "novnc";
    private static final int MAX_FRAME = 16 * 1024 * 1024;

    private final AssetManager assets;
    private final int listenPort;
    private final String vncHost;
    private final int vncPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile String lastError = "";

    ProxyServer(AssetManager assets, int listenPort, String vncHost, int vncPort) {
        this.assets = assets;
        this.listenPort = listenPort;
        this.vncHost = vncHost;
        this.vncPort = vncPort;
    }

    synchronized void start() throws IOException {
        if (running.get()) return;
        serverSocket = new ServerSocket(listenPort);
        running.set(true);
        lastError = "";
        acceptThread = new Thread(this::acceptLoop, "remote-browser-proxy-accept");
        acceptThread.start();
        Log.i(TAG, "listening on :" + listenPort + ", vnc=" + vncHost + ":" + vncPort);
    }

    boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    String getLastError() { return lastError; }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                workers.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                    Log.w(TAG, "accept failed", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = new BufferedOutputStream(c.getOutputStream());
            HttpRequest req = readRequest(in);
            if (req == null) return;
            if ("/websockify".equals(req.path)) {
                handleWebSocket(req, in, out, c);
            } else {
                handleStatic(req, out);
            }
            out.flush();
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            Log.d(TAG, "client ended: " + lastError);
        }
    }

    private void handleStatic(HttpRequest req, OutputStream out) throws IOException {
        if ("/".equals(req.path)) {
            writeText(out, "HTTP/1.1 302 Found\r\nLocation: /vnc.html\r\nContent-Length: 0\r\n\r\n");
            return;
        }
        String clean = normalizePath(req.path);
        if (clean == null) {
            writeStatus(out, 404, "Not Found", "not found");
            return;
        }
        String assetPath = ASSET_ROOT + "/" + clean;
        try (InputStream asset = assets.open(assetPath)) {
            byte[] body = readAll(asset);
            String type = contentType(clean);
            writeText(out, "HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\n\r\n");
            out.write(body);
        } catch (IOException e) {
            writeStatus(out, 404, "Not Found", "noVNC asset missing: " + clean + "\nRun scripts/prepare_companion_assets.sh before building the APK.\n");
        }
    }

    private void handleWebSocket(HttpRequest req, InputStream in, OutputStream out, Socket client) throws Exception {
        String upgrade = req.headers.getOrDefault("upgrade", "");
        String key = req.headers.get("sec-websocket-key");
        if (!"websocket".equalsIgnoreCase(upgrade) || key == null || key.isEmpty()) {
            writeStatus(out, 426, "Upgrade Required", "websocket upgrade required");
            out.flush();
            return;
        }

        Socket vnc = new Socket(vncHost, vncPort);
        vnc.setTcpNoDelay(true);
        String protocol = req.headers.getOrDefault("sec-websocket-protocol", "").contains("binary")
                ? "Sec-WebSocket-Protocol: binary\r\n" : "";
        writeText(out, "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + websocketAccept(key) + "\r\n" +
                protocol + "\r\n");
        out.flush();
        client.setSoTimeout(0);

        CountDownLatch done = new CountDownLatch(1);
        Thread a = new Thread(() -> { try { copyWsToTcp(in, vnc); } finally { done.countDown(); } }, "ws-to-vnc");
        Thread b = new Thread(() -> { try { copyTcpToWs(vnc, out); } finally { done.countDown(); } }, "vnc-to-ws");
        a.start();
        b.start();
        done.await();
        closeQuietly(vnc);
        closeQuietly(client);
    }

    private void copyWsToTcp(InputStream ws, Socket vnc) {
        try {
            OutputStream tcp = vnc.getOutputStream();
            while (running.get() && !vnc.isClosed()) {
                Frame frame = readFrame(ws);
                if (frame == null || frame.opcode == 0x8) break;
                if ((frame.opcode == 0x1 || frame.opcode == 0x2 || frame.opcode == 0x0) && frame.payload.length > 0) {
                    tcp.write(frame.payload);
                    tcp.flush();
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(vnc);
        }
    }

    private void copyTcpToWs(Socket vnc, OutputStream ws) {
        byte[] buf = new byte[32768];
        try {
            InputStream tcp = vnc.getInputStream();
            int n;
            while (running.get() && (n = tcp.read(buf)) != -1) {
                writeFrame(ws, 0x2, buf, n);
                ws.flush();
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(vnc);
        }
    }

    private static HttpRequest readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return null;
        String[] parts = requestLine.split(" ", 3);
        if (parts.length < 2) return null;
        HttpRequest req = new HttpRequest();
        req.method = parts[0];
        req.path = stripQuery(parts[1]);
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                req.headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
            }
        }
        return req;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) throw new IOException("header too large");
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private static String stripQuery(String raw) throws IOException {
        int q = raw.indexOf('?');
        String path = q >= 0 ? raw.substring(0, q) : raw;
        return URLDecoder.decode(path, "UTF-8");
    }

    private static String normalizePath(String path) {
        if (path == null || !path.startsWith("/")) return null;
        String p = path.substring(1);
        if (p.isEmpty()) return "vnc.html";
        if (p.contains("..") || p.startsWith("/")) return null;
        return p;
    }

    private static String websocketAccept(String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read();
        if (b1 < 0) return null;
        int opcode = b0 & 0x0f;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7f;
        if (len == 126) len = ((long) readByte(in) << 8) | readByte(in);
        else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
        }
        if (len > MAX_FRAME) throw new IOException("frame too large: " + len);
        byte[] mask = new byte[4];
        if (masked) readFully(in, mask, 4);
        byte[] payload = new byte[(int) len];
        readFully(in, payload, payload.length);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        return new Frame(opcode, payload);
    }

    private static void writeFrame(OutputStream out, int opcode, byte[] payload, int len) throws IOException {
        out.write(0x80 | opcode);
        if (len < 126) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >>> 8) & 0xff);
            out.write(len & 0xff);
        } else {
            out.write(127);
            long l = len;
            for (int i = 7; i >= 0; i--) out.write((int) ((l >>> (8 * i)) & 0xff));
        }
        out.write(payload, 0, len);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("unexpected eof");
        return b;
    }

    private static void readFully(InputStream in, byte[] out, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(out, off, len - off);
            if (n < 0) throw new IOException("unexpected eof");
            off += n;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String contentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }

    private static void writeStatus(OutputStream out, int code, String status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writeText(out, "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\n\r\n");
        out.write(bytes);
    }

    private static void writeText(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override public synchronized void close() {
        running.set(false);
        closeQuietly(serverSocket);
        workers.shutdownNow();
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }

    private static final class HttpRequest {
        String method;
        String path;
        Map<String, String> headers = new HashMap<>();
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;
        Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }
}
