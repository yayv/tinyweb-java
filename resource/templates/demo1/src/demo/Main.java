package demo;

import top.x0a.tinyweb.TinyWeb;

/**
 * 业务项目的入口：这就是 phar stub 的 Java 等价物——三行起服务。启动单进程单站点的开发模式。
 * 直接用 jar 的 Main-Class：
 *   java -Dtinyweb.home=. -cp out:../dist/tinyweb.jar top.x0a.tinyweb.Bootstrap 8080
 * 启动 tinyweb 框架提供的服务启动入口，实现单进程多站点模式，建议在生产环境使用。
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        TinyWeb.site()
                .home(System.getProperty("tinyweb.home", "."))
                .controllerPackage("demo.Controller")
                .listen(args.length > 0 ? Integer.parseInt(args[0]) : 8080)
                .run();
    }
}
