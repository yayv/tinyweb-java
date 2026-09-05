package top.x0a.tinyweb;

import java.util.Collections;
import java.util.Map;

/**
 * Config — 单个 host 的配置快照（进程级、只读）。
 * 对照 core.php: $_config（getConfig / getAllConfig）+ $_controller_map（ControllerMap）。
 *
 * PHP 的 $_config 是嵌套关联数组，getConfig($key,$subkey) 支持两级取值；
 * 这里用 Map&lt;String,Object&gt;（value 为 String 或 Map&lt;String,String&gt;）保持同样语义。
 *
 * 本类是业务代码读配置的入口，故保持 public；构造与路由映射查询是框架内部，包内可见。
 */
public final class Config {
    private final Map<String, Object> config;          // cfg.<host>.conf
    private final Map<String, String[]> controllerMap; // cmap.<host>.conf: "c/a"->[C,a] / "c"->[C]

    Config(Map<String, Object> config, Map<String, String[]> controllerMap) {
        this.config = config;
        this.controllerMap = controllerMap;
    }

    /** 对照 getConfig($key)：不存在返回 null（PHP 返回 false） */
    public Object getConfig(String key) {
        return config.get(key);
    }

    /** 对照 getConfig($key,$subkey)：两级取值 */
    @SuppressWarnings("unchecked")
    public String getConfig(String key, String subkey) {
        Object v = config.get(key);
        if (v instanceof Map<?, ?> m) {
            Object sv = ((Map<String, Object>) m).get(subkey);
            return sv == null ? null : sv.toString();
        }
        return null;
    }

    /** 顶层键取字符串（顶层 value 若是嵌套 map 则返回 null） */
    public String getString(String key) {
        Object v = config.get(key);
        return (v == null || v instanceof Map) ? null : v.toString();
    }

    public Map<String, Object> getAllConfig() {
        return Collections.unmodifiableMap(config);
    }

    // ---- 框架内部 ----

    /** app.controllerPackage：业务控制器所在包，反射加载被限制在这个前缀下 */
    String controllerPackage() {
        String v = getConfig("app", "controllerPackage");
        return v == null ? "" : v;
    }

    /** app.sessionCookie：会话 cookie 名，缺省 TCSESSID */
    String sessionCookieName() {
        String v = getConfig("app", "sessionCookie");
        return (v == null || v.isEmpty()) ? "TCSESSID" : v;
    }

    /** 对照 manualsession：置 1/true 则框架不自动开会话 */
    boolean manualSession() {
        Object v = getConfig("manualsession");
        if (v == null) return false;
        String s = v.toString();
        return s.equals("1") || s.equalsIgnoreCase("true");
    }

    /**
     * 对照 core.php::ControllerMap($c,$a)：
     *   命中 "c/a"  -&gt; 返回映射目标 [C, a]
     *   命中 "c"    -&gt; 返回 [map[c], "index"]
     *   都不命中    -&gt; 返回 [c, a]
     */
    String[] controllerMap(String c, String a) {
        String[] full = controllerMap.get(c + "/" + a);
        if (full != null && full.length >= 2) return new String[]{ full[0], full[1] };
        String[] single = controllerMap.get(c);
        if (single != null) return new String[]{ single[0], "index" };
        return new String[]{ c, a };
    }
}
