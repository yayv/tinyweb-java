package top.x0a.tinyweb;

/**
 * Bootstrap — jar 的 Main-Class：不写一行 Java 也能起服务。
 *
 * <pre>{@code
 * java -Dtinyweb.home=. -cp app.jar:tinyweb.jar top.x0a.tinyweb.Bootstrap [port]
 * }</pre>
 *
 * 配置来源（优先级由高到低）：命令行端口 &gt; -D 系统属性 &gt; configs/cfg.default.conf。
 * 想在代码里配就用 {@link TinyWeb#builder()}，本类只是它的一层 system-property 外壳。
 */
public final class Bootstrap {

    private Bootstrap() {}

    public static void main(String[] args) throws Exception {
        TinyWeb.Builder b = TinyWeb.builder()
                .home(System.getProperty("tinyweb.home", "."));

        String pkg = System.getProperty("tinyweb.controllerPackage");
        if (pkg != null && !pkg.isEmpty()) b.controllerPackage(pkg);

        Integer port = intOrNull(args.length > 0 ? args[0] : System.getProperty("tinyweb.port"));
        if (port != null) b.listen(port);

        b.run();
    }

    private static Integer intOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
