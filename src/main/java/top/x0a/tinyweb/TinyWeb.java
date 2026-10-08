package top.x0a.tinyweb;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TinyWeb — 框架的公开门面与启动入口。
 *
 * Java 没有 PHP "include 一下框架就活了"的语义，所以 phar 的等价物在这里是：
 * jar 提供 {@link Controller} / {@link Model} 基类 + 本类这个启动器，业务项目只写子类，
 * 启动时三行：
 *
 * <pre>{@code
 * TinyWeb.builder()
 *         .home("."),                       // configs/ 与 logs/ 所在目录
 *         .controllerPackage("com.x.c")
 *         .listen(8080)
 *         .run();
 * }</pre>
 *
 * 所有路径与包名都走参数注入，没有一处硬编码在 CWD——这是能打成 jar 给别的项目用的前提。
 */
public final class TinyWeb {

    private final HttpServer server;
    private final int configuredPort;

    private TinyWeb(HttpServer server, int configuredPort) {
        this.server = server;
        this.configuredPort = configuredPort;
    }

    public static Builder site() { return new Builder(); }

    /** 启动监听并阻塞，直到 {@link #stop()} 被调用 */
    public void start() throws IOException { server.start(); }

    /** 关闭监听套接字，让 start() 返回。可从任意线程调用 */
    public void stop() { server.stop(); }

    /** 实际绑定的端口；未启动时返回配置值（listen(0) 时启动后才知道真实端口） */
    public int port() {
        int b = server.boundPort();
        return b > 0 ? b : configuredPort;
    }

    /** 构建器：所有依赖显式注入，无静态全局状态 */
    public static final class Builder {
        private Path home = Paths.get(".");
        private Path configDir;
        private Path logDir;
        private Integer port;
        private Duration sessionTimeout =   Duration.ofMinutes(30);
        private final Map<String, String> overrides = new LinkedHashMap<>();

        private Builder() {}

        /** configs/ 与 logs/ 的父目录，默认当前目录 */
        public Builder home(String path) { return home(Paths.get(path)); }

        public Builder home(Path path) { this.home = path; return this; }

        /** 单独指定配置目录（默认 &lt;home&gt;/configs） */
        public Builder configDir(Path path) { this.configDir = path; return this; }

        /** 单独指定日志目录（默认 &lt;home&gt;/logs） */
        public Builder logDir(Path path) { this.logDir = path; return this; }

        /** 业务控制器所在包，覆盖配置文件的 app.controllerPackage */
        public Builder controllerPackage(String pkg) {
            return config("app.controllerPackage", pkg);
        }

        /** 监听端口。0 表示由系统分配，启动后用 {@link TinyWeb#port()} 取真实端口 */
        public Builder listen(int port) { this.port = port; return this; }

        /** 会话空闲超时，默认 30 分钟 */
        public Builder sessionTimeout(Duration timeout) { this.sessionTimeout = timeout; return this; }

        /** 任意配置项的代码级覆盖，优先级高于配置文件。key 支持 "a.b" 两级 */
        public Builder config(String key, String value) {
            overrides.put(key, value);
            return this;
        }

        public TinyWeb build() {
            Path cfgDir = configDir != null ? configDir : home.resolve("configs");
            Path lgDir  = logDir    != null ? logDir    : home.resolve("logs");

            ConfigRegistry registry = new ConfigRegistry(cfgDir, overrides);
            SessionStore sessions = new SessionStore(sessionTimeout.toNanos());
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            FrontController front = new FrontController(registry, sessions, loader, lgDir);

            int resolved = resolvePort(registry);
            return new TinyWeb(new HttpServer(resolved, front::handle), resolved);
        }

        /** 构建并阻塞运行 */
        public void run() throws IOException { build().start(); }

        /** 端口优先级：listen() &gt; cfg.default.conf 的 app.port &gt; 8080 */
        private int resolvePort(ConfigRegistry registry) {
            if (port != null) return port;
            String cfgPort = registry.forHost("default").getConfig("app", "port");
            if (cfgPort != null) {
                try { return Integer.parseInt(cfgPort.trim()); } catch (NumberFormatException ignore) { }
            }
            return 8080;
        }
    }
}
