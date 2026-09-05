package top.x0a.tinyweb;

import java.util.Map;

/**
 * UrlParser — 对照 core.php::rebuildUrl()（框架内部）。
 * 把 /controller/action/key1-value1/key2-value2 解析为 controller/action/method，
 * 并把每段 key-value 填进 params（对照原来往 $_GET 里塞的行为）。
 */
final class UrlParser {

    private UrlParser() {}

    record Route(String controller, String action, String method) {}

    static Route rebuild(String uri, String base, Map<String, String> params) {
        // 去掉 base 前缀（对照 0===strpos($uri,$base) 分支）
        if (base != null && !base.isEmpty() && uri.startsWith(base)) {
            uri = uri.substring(base.length());
        }
        // 去掉 query string（query 已由 HttpServer 解析进 params）
        int q = uri.indexOf('?');
        String pathPart = q >= 0 ? uri.substring(0, q) : uri;

        String[] segs = pathPart.split("/", -1);
        String controller = "", action = "", method = "";

        for (int p = 0; p < segs.length; p++) {
            String v = segs[p];
            params.put("params" + p, v);

            String kv;
            int dash = v.indexOf('-');
            if (dash < 0) {
                kv = v;                              // 对照 strstr($v,'-',true)===false
            } else {
                kv = v.substring(0, dash);
                params.put(kv, v.substring(dash + 1));
            }
            switch (p) {
                case 0 -> controller = kv;
                case 1 -> action = kv;
                case 2 -> method = kv;
                default -> { }
            }
        }
        if (controller.isEmpty()) controller = "defaultcontroller";
        if (action.isEmpty()) action = "index";
        if (method.isEmpty()) method = "index";

        params.put("controller", controller);
        params.put("action", action);
        params.put("method", method);
        return new Route(controller, action, method);
    }
}
