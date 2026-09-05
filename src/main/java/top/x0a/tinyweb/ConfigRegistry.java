package top.x0a.tinyweb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigRegistry — 进程级配置注册表（框架内部）。
 * 对照 core.php::loadConfig($host)，但从"每请求单例"提升为"进程级、按 host 懒加载 + 缓存"。
 * 长驻服务器里配置只读、可跨请求共享；用 ConcurrentHashMap 保证并发安全。
 *
 * 配置目录默认 &lt;home&gt;/configs/，格式为 key=value 行、# 注释、host 缺省回退 default：
 *   cfg.&lt;host&gt;.conf   -&gt;  $_config        （支持 key.subkey=value 两级嵌套）
 *   cmap.&lt;host&gt;.conf  -&gt;  $_controller_map（c/a = C/a  或  c = C）
 *
 * overrides 是 TinyWeb.Builder 传进来的代码级覆盖（如 app.controllerPackage），
 * 优先级高于配置文件——这样"三行 Main 启动"不必先摆一个 configs 目录。
 */
final class ConfigRegistry {
    private final Path configDir;
    private final Map<String, String> overrides;
    private final Map<String, Config> cache = new ConcurrentHashMap<>();

    ConfigRegistry(Path configDir, Map<String, String> overrides) {
        this.configDir = configDir;
        this.overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }

    Config forHost(String host) {
        if (host == null || host.isEmpty()) host = "default";
        int colon = host.indexOf(':');            // example.com:8080 -> example.com
        String h = colon >= 0 ? host.substring(0, colon) : host;
        return cache.computeIfAbsent(h, this::load);
    }

    private Config load(String host) {
        Map<String, Object> cfg = parseKeyValue(resolve("cfg", host));
        overrides.forEach((k, v) -> put(cfg, k, v));
        Map<String, String[]> cmap = parseCmap(resolve("cmap", host));
        return new Config(cfg, cmap);
    }

    /** cfg.&lt;host&gt;.conf 不存在则回退 cfg.default.conf（对照 PHP loadConfig 的回退） */
    private Path resolve(String prefix, String host) {
        Path p = configDir.resolve(prefix + "." + host + ".conf");
        if (Files.isRegularFile(p)) return p;
        return configDir.resolve(prefix + ".default.conf");
    }

    /** key=value / key.subkey=value（两级嵌套），# 注释 */
    private Map<String, Object> parseKeyValue(Path path) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String[] kv : readPairs(path)) put(out, kv[0], kv[1]);
        return out;
    }

    /** 写入一个 key（"a.b" 视作两级嵌套） */
    private static void put(Map<String, Object> out, String key, String val) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            out.put(key, val);
            return;
        }
        String k = key.substring(0, dot), sub = key.substring(dot + 1);
        Object existing = out.get(k);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (existing instanceof Map)
                ? (Map<String, Object>) existing
                : new LinkedHashMap<>();
        nested.put(sub, val);
        out.put(k, nested);
    }

    /** cmap value 形如 "C/a" 或 "C" */
    private Map<String, String[]> parseCmap(Path path) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String[] kv : readPairs(path)) {
            String key = kv[0], val = kv[1];
            int slash = val.indexOf('/');
            if (slash >= 0)
                out.put(key, new String[]{ val.substring(0, slash), val.substring(slash + 1) });
            else
                out.put(key, new String[]{ val });
        }
        return out;
    }

    private List<String[]> readPairs(Path path) {
        List<String[]> pairs = new ArrayList<>();
        if (!Files.isRegularFile(path)) return pairs;   // 缺文件按空，对照 PHP require 回退语义
        try {
            for (String raw : Files.readAllLines(path)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                pairs.add(new String[]{ line.substring(0, eq).strip(), line.substring(eq + 1).strip() });
            }
        } catch (IOException e) {
            // 读失败按空处理
        }
        return pairs;
    }
}
