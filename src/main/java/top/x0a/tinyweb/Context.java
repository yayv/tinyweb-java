package top.x0a.tinyweb;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context — 请求级上下文，也是业务代码唯一直接摸到的框架状态。
 *
 * 承接 core.php 里"每请求"的那部分职责：_log / _callstack / shutdown / writeLog，
 * 加上 PHP 超全局 $_GET / $_POST / $_SERVER / php://input 的等价物，以及完整的响应构造
 * （状态码 / 响应头 / Cookie / 正文），替代 PHP 的 echo 直写 stdout。
 *
 * 每个请求一个新实例，天然线程隔离——这也是把 model::$_error 从 static 下沉到这里的原因。
 *
 * 公开面刻意收窄：读取器 + 响应构造是 public（业务用），所有写入器与 callstack/日志落盘
 * 是包内可见（框架用）。业务代码拿不到也改不了请求输入。
 */
public final class Context {

    // --- 请求输入（对照 $_SERVER / $_GET / php://input）---
    private String host = "";
    private String method = "GET";
    private String uri = "/";
    private String body = "";
    private final Map<String, String> headers = new HashMap<>();
    /** 对照 $_GET：query string + rebuildUrl 从路径段解析出的 key-value */
    private final Map<String, String> params = new LinkedHashMap<>();
    /** 对照 $_POST：form-urlencoded body，首次访问时懒解析 */
    private Map<String, String> form;

    // --- 路由结果 ---
    private String controller = "";
    private String action = "";

    // --- 会话（对照 $_SESSION）---
    private Session session;

    // --- 响应 ---
    private int status = 200;
    private String contentType = "text/html; charset=utf-8";
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final List<String> cookies = new ArrayList<>();
    private final StringBuilder response = new StringBuilder();

    // --- 日志 / callstack（对照 core.php）---
    private final List<String> log = new ArrayList<>();
    private final Deque<String> callstack = new ArrayDeque<>();

    // --- per-request 错误栈（对照 model::$_error，从 static 降级为请求级）---
    private final Deque<Map<String, Object>> errors = new ArrayDeque<>();

    /** 由处理本请求的站点设置（FrontController.handle 第一步） */
    private Path logDir;

    Context() {}

    // ================= 请求读取（public：业务代码用） =================

    public String host()   { return host; }
    public String method() { return method; }
    public String uri()    { return uri; }
    /** 原始请求体，对照 php://input */
    public String body()   { return body; }

    /** 请求头，名字大小写不敏感 */
    public String header(String name) {
        return name == null ? null : headers.get(name.toLowerCase());
    }

    public Map<String, String> headers() { return Collections.unmodifiableMap(headers); }

    /** 对照 $_GET[$key] */
    public String param(String key) { return params.get(key); }

    public String param(String key, String fallback) {
        String v = params.get(key);
        return v == null ? fallback : v;
    }

    public Map<String, String> params() { return Collections.unmodifiableMap(params); }

    /** 对照 $_POST[$key]：仅当 Content-Type 为 application/x-www-form-urlencoded 时有值 */
    public String form(String key) { return formMap().get(key); }

    public Map<String, String> form() { return Collections.unmodifiableMap(formMap()); }

    public String controller() { return controller; }
    public String action()     { return action; }
    /** 对照 $_SESSION 的持有者；manualsession=1 时为 null */
    public Session session()   { return session; }

    // ================= 响应构造（public：业务代码用） =================

    /** 对照 echo：追加响应正文 */
    public void write(String s) { if (s != null) response.append(s); }

    /** 设置状态码，返回 this 以便链式调用 */
    public Context status(int code) { this.status = code; return this; }

    public int status() { return status; }

    public Context contentType(String type) { this.contentType = type; return this; }

    /** 设置响应头（同名覆盖）。Content-Type / Content-Length / Set-Cookie 走各自的入口 */
    public Context setHeader(String name, String value) {
        if (name == null) return this;
        if (name.equalsIgnoreCase("content-type")) return contentType(value);
        if (name.equalsIgnoreCase("set-cookie"))   { cookies.add(value); return this; }
        if (name.equalsIgnoreCase("content-length")) return this;   // 由 HttpServer 计算
        responseHeaders.put(name, value);
        return this;
    }

    /** 追加一条 Set-Cookie，attrs 形如 "Path=/", "HttpOnly", "Max-Age=3600" */
    public Context cookie(String name, String value, String... attrs) {
        StringBuilder sb = new StringBuilder(name).append('=').append(value);
        for (String a : attrs) sb.append("; ").append(a);
        cookies.add(sb.toString());
        return this;
    }

    /** 正文置为 JSON（调用方负责序列化——框架零依赖，不带 JSON 库） */
    public Context json(String jsonText) {
        contentType("application/json; charset=utf-8");
        response.setLength(0);
        response.append(jsonText);
        return this;
    }

    /** 302 重定向，正文清空 */
    public Context redirect(String location) { return redirect(location, 302); }

    public Context redirect(String location, int code) {
        status(code);
        responseHeaders.put("Location", location);
        response.setLength(0);
        return this;
    }

    // ================= 日志 / 错误栈（public：业务代码可用） =================

    /** 对照 core.php::pushLog */
    public void pushLog(String line) { log.add(line); }

    /** 对照 model::pushError */
    public void pushError(Object params, String msg) {
        Map<String, Object> e = new HashMap<>();
        e.put("params", params);
        e.put("msg", msg);
        errors.push(e);
    }

    /** 对照 model::popError */
    public Map<String, Object> popError() { return errors.poll(); }

    // ================= 以下为框架内部（包内可见） =================

    void setHost(String v)    { this.host = v == null ? "" : v; }
    void setMethod(String v)  { this.method = v; }
    void setUri(String v)     { this.uri = v; }
    void setBody(String v)    { this.body = v == null ? "" : v; }
    void putHeader(String name, String value) { headers.put(name, value); }
    void putParam(String key, String value)   { params.put(key, value); }
    Map<String, String> mutableParams()       { return params; }
    void setController(String v) { this.controller = v; }
    void setAction(String v)     { this.action = v; }
    void setSession(Session s)   { this.session = s; }
    void setLogDir(Path dir)     { this.logDir = dir; }

    String responseBody() { return response.toString(); }
    String responseContentType() { return contentType; }
    Map<String, String> responseHeaders() { return responseHeaders; }
    List<String> responseCookies() { return cookies; }

    /** 对照 RegisterShutdown */
    void registerShutdown(String funcname) { callstack.push(funcname); }

    /** 对照 UnregisterShutdown */
    void unregisterShutdown(String funcname) {
        String old = callstack.poll();
        if (old != null && !old.equals(funcname)) {
            pushLog("WARNING: wrong sequence of UnregisterShutdown\n");
            callstack.push(old);
            callstack.push(funcname);
        }
    }

    /** 对照 shutdown()：请求结束时（try/finally）调用；callstack 非空说明 action 异常退出 */
    void shutdown() {
        if (!callstack.isEmpty()) {
            pushLog("CallStack is NOT empty\n");
            for (String v : callstack) pushLog(v);
            pushLog("\n");
        }
        writeLog();
    }

    /** 对照 writeLog：落盘 logs/crumbs.<date>.txt */
    void writeLog() {
        if (log.isEmpty() || logDir == null) return;
        Path file = logDir.resolve("crumbs." + LocalDate.now() + ".txt");
        try {
            Files.createDirectories(logDir);
            StringBuilder sb = new StringBuilder();
            for (String l : log) sb.append(l);
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 与 PHP error_log 尽量一致：写失败静默
        }
    }

    private Map<String, String> formMap() {
        if (form == null) {
            form = new LinkedHashMap<>();
            String ct = headers.get("content-type");
            if (ct != null && ct.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
                parseUrlEncoded(body, form);
            }
        }
        return form;
    }

    /** query string / form body 通用解析：a=1&amp;b=2，百分号解码 */
    static void parseUrlEncoded(String s, Map<String, String> into) {
        if (s == null || s.isEmpty()) return;
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            into.put(decode(k), decode(v));
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;   // 非法百分号编码：原样保留，不因畸形输入 500
        }
    }
}
