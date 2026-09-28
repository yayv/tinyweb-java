package demo;

import top.x0a.tinyweb.TinyWeb;

public class Main {
    public static void main(String[] args) throws Exception {
        MyBatisUtil.initDb();

        TinyWeb.site()
                .home(System.getProperty("tinyweb.home", "."))
                .controllerPackage("demo")
                .listen(args.length > 0 ? Integer.parseInt(args[0]) : 8081)
                .run();
    }
}
