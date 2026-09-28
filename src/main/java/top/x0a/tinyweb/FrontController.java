package top.x0a.tinyweb;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * FrontController — 对照 web_index.php 的请求派发流水线（框架内部）：
 * loadConfig(host) -&gt; 日志 -&gt; session -&gt; 解析 URL/路由 -&gt; loadController -&gt; 派发 action -&gt; writeLog。
 *
 * 与 web_index.php 顶层脚本的区别：那是"每请求一次"的脚本，这里是长驻服务器里被反复调用的方法，
 * 所以配置从 ConfigRegistry 取（进程级缓存），每请求状态在传入的 Context 上。
 */
final class FrontController {
    /** 控制器名只允许这个字符集：反射加载前的第一道闸，防止 URL 段越出配置的包 */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final ConfigRegistry registry;
    private final SessionStore sessions;
    /** 从哪个 classloader 找控制器：单站点是应用 classpath，多站点是该站 jar 的 loader */
    private final ClassLoader loader;
    private final Path logDir;

    FrontController(ConfigRegistry registry, SessionStore sessions, ClassLoader loader, Path logDir) {
        this.registry = registry;
        this.sessions = sessions;
        this.loader = loader;
        this.logDir = logDir;
    }

    void handle(Context ctx) {
        ctx.setLogDir(logDir);
        Config config = registry.forHost(ctx.host());

        ctx.pushLog("URL:" + ctx.uri() + "\n");
        ctx.pushLog("METHOD:" + ctx.method() + "\n");
        if ("POST".equals(ctx.method())) {
            ctx.pushLog("POST_BODY:" + ctx.body() + "\n");   // 对照读取 php://input
        }
        ctx.pushLog("start(url):" + micro() + "\n");

        // session（对照 manualsession 分支：手动模式则什么都不做）
        if (!config.manualSession()) {
            String cookieName = config.sessionCookieName();
            String incoming = cookie(ctx, cookieName);
            Session s = sessions.start(incoming);
            ctx.setSession(s);
            // 会话 id 变了（首次访问，或客户端带来的 id 已过期/伪造）才下发 Set-Cookie。
            // 这一步是原实现漏掉的：不下发 cookie，每个请求都会拿到全新会话。
            if (!s.id().equals(incoming)) {
                ctx.cookie(cookieName, s.id(), "Path=/", "HttpOnly", "SameSite=Lax");
            }
        }

        // 解析 controller/action（对照 index.php 模式 vs rewrite 模式）
        String controller, action;
        String qc = ctx.param("controller");
        if (qc != null && !qc.isEmpty()) {
            // /index.php?controller=a&action=b&method=c 模式（query 参数已由 HttpServer 预填）
            controller = qc;
            action = ctx.param("action", "index");
        } else {
            String base = pathOf(config.getString("sitebase"));
            UrlParser.Route r = UrlParser.rebuild(ctx.uri(), base, ctx.mutableParams());
            String[] mapped = config.controllerMap(r.controller(), r.action());
            controller = mapped[0];
            action = mapped[1];
        }
        ctx.setController(controller);
        ctx.setAction(action);

        Controller c = loadController(config, ctx, controller);

        // clicklog（对照 getConfig('clicklog')）
        if (config.getConfig("clicklog") != null) {
            ctx.pushLog("clicklog: " + config.getConfig("clicklog") + "\n");
        }

        // 对照 method_exists 分支：action 不存在先回退成 index，**再**记日志。
        // 顺序不能反——否则 start/end_controller 会记下一个根本没执行的方法名，
        // 而 registerShutdown 记的又是真正执行的那个，两处自相矛盾。
        Method target = findAction(c, action);
        if (target == null) {
            action = "index";
            ctx.setAction(action);
        }

        ctx.pushLog("start_controller(" + controller + "->" + action + "):" + micro() + "\n");

        // 派发（对照 Register/UnregisterShutdown）
        try {
            ctx.registerShutdown(controller + "->" + action);
            if (target != null) {
                target.invoke(c);
            } else {
                c.index();
            }
            ctx.unregisterShutdown(controller + "->" + action);
        } catch (Exception e) {
            // action 抛异常：不 Unregister，留给 shutdown() 记 "CallStack is NOT empty"
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            ctx.pushLog("EXCEPTION(" + controller + "->" + action + "): " + cause + "\n");
            ctx.status(500).contentType("text/plain; charset=utf-8");
            ctx.write("500 Internal Server Error\n");
        } finally {
            ctx.pushLog("end_controller(" + controller + "->" + action + "):" + micro() + "\n");
            ctx.pushLog("end(url):" + micro() + "\n");
            ctx.shutdown();  // 对照 register_shutdown_function：callstack 校验 + writeLog
        }
    }

    /**
     * 对照 core.php::loadController：按名反射，缺失先回退站点自己的 DefaultController
     * （对照每个项目的 c/defaultcontroller.php），站点没写才用框架的。
     * 三道约束（原实现只有第一道）：名字字符集、限定在 app.controllerPackage 下、
     * 必须是 Controller 的具体子类——否则 URL 段等于 new 任意 classpath 上的类。
     */
    private Controller loadController(Config config, Context ctx, String name) {
        Controller c = instantiate(config, ctx, name);
        if (c != null) return c;
        ctx.pushLog("Controller(" + name + ") not found, using DefaultController\n");
        c = instantiate(config, ctx, "DefaultController");
        if (c != null) return c;
        DefaultController d = new DefaultController();
        d.bind(ctx, config, null);
        return d;
    }

    private Controller instantiate(Config config, Context ctx, String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return null;
        String pkg = config.controllerPackage();
        String cls = (pkg.isEmpty() ? "" : pkg + ".") + capitalize(name);
        try {
            Class<?> k = Class.forName(cls, true, loader);
            if (!Controller.class.isAssignableFrom(k)) return null;
            if (Modifier.isAbstract(k.getModifiers())) return null;
            Controller c = (Controller) k.getDeclaredConstructor().newInstance();
            c.bind(ctx, config, null);
            return c;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** 对照 method_exists：找无参 public 方法；框架基类自身的方法不可当 action */
    private Method findAction(Controller c, String action) {
        if (action == null || action.isEmpty()) return null;
        try {
            Method m = c.getClass().getMethod(action);
            if (m.getDeclaringClass() == Controller.class || m.getDeclaringClass() == Object.class) return null;
            if (m.getParameterCount() != 0) return null;
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String cookie(Context ctx, String name) {
        String h = ctx.header("cookie");
        if (h == null) return null;
        for (String part : h.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    private static String pathOf(String url) {
        if (url == null || url.isEmpty()) return "/";
        try {
            String p = URI.create(url).getPath();
            return (p == null || p.isEmpty()) ? "/" : p;
        } catch (Exception e) {
            return "/";
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 对照 PHP microtime()：给日志一个时间戳标记 */
    private static String micro() {
        return String.format("%.6f", System.nanoTime() / 1_000_000_000.0);
    }
}
