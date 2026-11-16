package dev.luacomp.lua;

import dev.luacomp.LuaCompConfig;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API http do computador: requisições assíncronas com whitelist de domínios, bloqueio de
 * endereços privados, limite de tamanho e redirecionamentos revalidados a cada salto.
 * O resultado chega como evento http_success / http_failure.
 */
public final class HttpApi {
    private HttpApi() {}

    private static final String PASTEBIN_POST = "https://pastebin.com/api/api_post.php";
    private static final int MAX_REDIRECTS = 5;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "LuaComp-Http");
        t.setDaemon(true);
        return t;
    });

    private static final class HttpFailure extends Exception {
        final LuaTable handle;
        HttpFailure(String message, LuaTable handle) {
            super(message);
            this.handle = handle;
        }
    }

    public static void install(Globals g, LuaMachine machine) {
        LuaTable http = new LuaTable();

        LuaApis.fn(http, "checkURL", a -> {
            String err = validate(a.checkjstring(1));
            return err == null ? LuaValue.TRUE : LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(err));
        });

        LuaApis.fn(http, "request", a -> {
            String url = a.checkjstring(1);
            String body = a.isnil(2) ? null : a.checkjstring(2);
            Map<String, String> headers = readHeaders(a.opttable(3, null));
            return start(machine, url, body, headers, url);
        });

        LuaApis.fn(http, "pastebinPut", a -> {
            String key = LuaCompConfig.pastebinKey();
            if (key == null || key.isBlank()) {
                return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf("Pastebin API key not configured on this server"));
            }
            String form = "api_option=paste"
                    + "&api_dev_key=" + enc(key)
                    + "&api_paste_format=lua"
                    + "&api_paste_name=" + enc(a.checkjstring(1))
                    + "&api_paste_code=" + enc(a.checkjstring(2));
            return start(machine, PASTEBIN_POST, form, new LinkedHashMap<>(), PASTEBIN_POST);
        });

        LuaApis.fn(http, "storeUrl", a -> LuaValue.valueOf(LuaCompConfig.storeIndexUrl()));
        g.set("http", http);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> readHeaders(LuaTable t) {
        Map<String, String> m = new LinkedHashMap<>();
        if (t == null) return m;
        LuaValue k = LuaValue.NIL;
        while (true) {
            Varargs n = t.next(k);
            k = n.arg1();
            if (k.isnil()) break;
            m.put(k.tojstring(), n.arg(2).tojstring());
        }
        return m;
    }

    private static Varargs start(LuaMachine machine, String url, String body, Map<String, String> headers, String eventUrl) {
        String err = validate(url);
        if (err != null) return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(err));
        if (!machine.tryAcquireHttp()) return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf("Too many ongoing requests"));

        POOL.execute(() -> {
            try {
                LuaTable handle = perform(url, body, headers);
                machine.queueEvent("http_success", eventUrl, handle);
            } catch (HttpFailure f) {
                if (f.handle != null) machine.queueEvent("http_failure", eventUrl, f.getMessage(), f.handle);
                else machine.queueEvent("http_failure", eventUrl, f.getMessage());
            } catch (RuntimeException e) {
                machine.queueEvent("http_failure", eventUrl, "Request failed");
            } finally {
                machine.releaseHttp();
            }
        });
        return LuaValue.TRUE;
    }

    // ------------------------------------------------------------ validação

    public static String validate(String url) {
        if (!LuaCompConfig.httpEnabled()) return "HTTP is disabled";
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return "Invalid URL";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return "Invalid protocol";
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return "Invalid URL";
        host = host.toLowerCase(Locale.ROOT);
        if (!allowed(host)) return "Domain not permitted";
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivate(addr)) return "Domain not permitted";
            }
        } catch (UnknownHostException e) {
            return "Could not resolve host";
        }
        return null;
    }

    private static boolean allowed(String host) {
        for (String entry : LuaCompConfig.httpAllow()) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.equals("*") || host.equals(e) || host.endsWith("." + e)) return true;
        }
        return false;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) return (b[0] & 0xFE) == 0xFC;              // fc00::/7
        if (b[0] == 0) return true;                                                // 0.0.0.0/8
        return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;                        // 100.64.0.0/10
    }

    // ------------------------------------------------------------ execução

    private static LuaTable perform(String url, String body, Map<String, String> headers) throws HttpFailure {
        String current = url;
        String method = body == null ? "GET" : "POST";
        byte[] data = body == null ? null : body.getBytes(StandardCharsets.UTF_8);

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            String err = validate(current);
            if (err != null) throw new HttpFailure(err, null);

            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(current))
                    .timeout(Duration.ofMillis(LuaCompConfig.httpTimeoutMs()));
            boolean hasUa = false, hasCt = false;
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT);
                if (k.equals("user-agent")) hasUa = true;
                if (k.equals("content-type")) hasCt = true;
                try { rb.header(e.getKey(), e.getValue()); } catch (IllegalArgumentException ignored) { }
            }
            if (!hasUa) rb.header("User-Agent", "LuaComp/0.1");
            if (data != null && !hasCt) rb.header("Content-Type", "application/x-www-form-urlencoded");
            rb.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(data));

            HttpResponse<InputStream> resp;
            try {
                resp = CLIENT.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException e) {
                throw new HttpFailure("Timed out", null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpFailure("Interrupted", null);
            } catch (IOException | RuntimeException e) {
                throw new HttpFailure("Could not connect", null);
            }

            int code = resp.statusCode();
            var location = resp.headers().firstValue("location");
            if (code >= 300 && code < 400 && location.isPresent()) {
                try { resp.body().close(); } catch (IOException ignored) { }
                try {
                    current = URI.create(current).resolve(location.get()).toString();
                } catch (RuntimeException e) {
                    throw new HttpFailure("Invalid redirect", null);
                }
                if (code != 307 && code != 308) {
                    method = "GET";
                    data = null;
                }
                continue;
            }

            byte[] content = readLimited(resp.body());
            LuaTable h = LuaApis.readHandle(new String(content, StandardCharsets.UTF_8));
            final int status = code;
            LuaApis.fn(h, "getResponseCode", a -> LuaValue.valueOf(status));
            LuaTable hdrs = new LuaTable();
            resp.headers().map().forEach((k, v) -> { if (!v.isEmpty()) hdrs.set(k, LuaValue.valueOf(v.get(0))); });
            LuaApis.fn(h, "getResponseHeaders", a -> hdrs);

            if (code >= 400) throw new HttpFailure("HTTP " + code, h);
            return h;
        }
        throw new HttpFailure("Too many redirects", null);
    }

    private static byte[] readLimited(InputStream in) throws HttpFailure {
        int max = LuaCompConfig.httpMaxBytes();
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0, n;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > max) throw new HttpFailure("Response too large", null);
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new HttpFailure("Connection lost", null);
        }
    }
}
