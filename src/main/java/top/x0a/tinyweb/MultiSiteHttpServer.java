package top.x0a.tinyweb;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * MultiSiteHttpServer — 在 HttpServer 基础上支持多站点。
 *
 * 在 parseRequest 之后、handler 之前，根据 Host 请求头选择对应站点的解析器。
 * 如果 Host 对应的站点已加载，使用该站点的解析器（FrontController 或任意 Consumer<Context>）；
 * 否则使用默认站点的解析器。
 */
final class MultiSiteHttpServer extends HttpServer {
    /** host（小写、不含端口）→ 站点的解析器 */
    private final Map<String, Consumer<Context>> byHost;
    private final Consumer<Context> defaultHandler;

    /** 接收 Map&lt;host, Consumer&lt;Context&gt;&gt;，Consumer 通常是 frontController::handle */
    MultiSiteHttpServer(int port, Map<String, Consumer<Context>> byHost, Consumer<Context> defaultHandler) {
        super(port, defaultHandler);   // fallback，通常不会用到
        this.byHost = new HashMap<>(byHost);
        this.defaultHandler = defaultHandler;
    }

    @Override
    protected Consumer<Context> selectHandler(Context ctx) {
        return byHost.getOrDefault(hostOnly(ctx.host()), defaultHandler);
    }

    /** 与 ConfigRegistry.forHost 一致：去掉端口；另外转小写，Host 头大小写不敏感 */
    private static String hostOnly(String host) {
        int colon = host.indexOf(':');
        return (colon >= 0 ? host.substring(0, colon) : host).toLowerCase();
    }
}
