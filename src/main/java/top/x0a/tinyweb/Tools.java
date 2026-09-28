package top.x0a.tinyweb;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Tools — 框架的工具集和MultiSite启动器
 *
 * 启动方法：
 * {@code java -cp app.jar:tinyweb.jar top.x0a.tinyweb.Tools.MultiSite 8080}。
 *
 * 当前工具类可以提供的方法包括：
 *  1. 生成默认站点, 接受无头请求，接受错误<域名:端口>请求头等
 *  2. 检查当前目录下可以被正确载入的站点，并打印列表
 *  3. 输出指定站点的配置错误信息
 *  4. 启动多站点模式
 *  
 */
public final class Tools {

    static final String DEFAULT_SITE = "default";
    private static final String JAR_PREFIX = "site-";

    private final Path home;
    private HttpServer server;

    /** host（小写、不含端口）→ 站点；启动时由各站 configs/ 下的 cfg.&lt;host&gt;.conf 文件名建立 */
    private final Map<String, Site> byHost = new HashMap<>();
    private Site defaultSite;

    private record Site(String name, FrontController front, ClassLoader loader) {}

    public Tools(Path home) {
        // TODO: finish constructor
        this.home = home;
    }

    /** 多站点模式启动监听并阻塞，直到 {@link #stop()} 被调用 */
    public void runMultiSite(int port) throws IOException {
        List<String> names = checkDirs();
        if (!names.contains(DEFAULT_SITE)) {
            throw new IllegalStateException("多站点模式必须部署默认站点，先执行：java -jar tinyweb.jar gen-default");
        }
        if (!conflicts(names).isEmpty()) {
            throw new IllegalStateException("有 host 被多个站点认领，拒绝启动");
        }

        System.out.println();
        System.out.println("生效的配置文件：");
        for (String name : names) {
            Path dir = home.resolve(name);
            URLClassLoader loader = new URLClassLoader(name,
                    new URL[]{ jarOf(name).toUri().toURL() }, Tools.class.getClassLoader());
            FrontController front = new FrontController(
                    new ConfigRegistry(dir.resolve("configs"), Map.of()),
                    new SessionStore(Duration.ofMinutes(30).toNanos()),
                    loader, dir.resolve("logs"));
            Site site = new Site(name, front, loader);

            List<String> report = new ArrayList<>();
            if (name.equals(DEFAULT_SITE)) {
                defaultSite = site;
                report.add("(无头/未登记 Host) -> " + name + "/configs/cfg.default.conf");
            }
            for (String host : hostsOf(name)) {
                byHost.put(host, site);
                report.add(host + " -> " + name + "/configs/cfg." + host + ".conf");
            }
            for (String line : report) System.out.println("  " + line);
            appendStartupLog(dir.resolve("logs"), report);
        }
        System.out.println();

        server = new HttpServer(port, this::dispatch);
        server.start();
    }

    /** 关闭监听套接字，让 start() 返回。可从任意线程调用 */
    public void stop() {
        if (server != null) server.stop();
    }

    /** 按 Host 选站点，找不到就交给默认站点 */
    private void dispatch(Context ctx) {
        Site site = byHost.getOrDefault(hostOnly(ctx.host()), defaultSite);
        Thread t = Thread.currentThread();
        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(site.loader());   // 站内代码及它用的库，按本站的 classloader 找类
        try {
            site.front().handle(ctx);
        } finally {
            t.setContextClassLoader(old);
        }
    }

    /** TODO: 生成默认站点的源代码 */
    public Path genDefaultSite() throws IOException {
        Path dir = home.resolve(DEFAULT_SITE);
        Path src = dir.resolve("src/defaultsite/DefaultController.java");
        writeIfMissing(src, DEFAULT_CONTROLLER);
        writeIfMissing(dir.resolve("configs/cfg.default.conf"), DEFAULT_CONFIG);
        Files.createDirectories(dir.resolve("logs"));

        Path jar = jarOf(DEFAULT_SITE);
        compileToJar(src, jar);
        System.out.println("编译: " + src + " -> " + jar);
        return jar;
    }

    /** 从 demo1 模板生成项目：读取 jar 内资源、替换包名、写到目标目录 */
    public Path genProject(String targetDir, String packageName) throws IOException {
        Path dir = Paths.get(targetDir);
        String templateBase = "templates/demo1";
        String packagePath = packageName.replace('.', '/');

        // 从 jar 包或文件系统读取模板文件
        String selfJarPath;
        try {
            selfJarPath = Paths.get(Tools.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("无法确定框架 jar 位置", e);
        }

        for (String path : TEMPLATE_FILES) {
            String templatePath = templateBase + "/" + path;
            byte[] content = readTemplateFile(selfJarPath, templatePath);
            if (content == null) {
                System.err.println("警告：模板文件不存在: " + templatePath);
                continue;
            }

            String targetPath = replacePath(path, packagePath);
            Path targetFile = dir.resolve(targetPath);
            String contentStr = new String(content);

            if (path.endsWith(".java") || path.endsWith(".conf") || path.endsWith(".sh")) {
                contentStr = replaceContent(contentStr, packageName, packagePath);
            }

            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, contentStr);
            System.out.println("生成: " + targetFile);
        }

        return dir;
    }

    /**
     * 从 jar 包或文件系统读取模板文件
     * @param jarPath jar 文件路径（或 null 表示不是 jar）
     * @param resourcePath 资源路径
     * @return 文件内容，不存在返回 null
     */
    private byte[] readTemplateFile(String jarPath, String resourcePath) throws IOException {
        if (jarPath.endsWith(".jar")) {
            try (JarFile jar = new JarFile(jarPath)) {
                JarEntry entry = jar.getJarEntry(resourcePath);
                if (entry != null) {
                    return jar.getInputStream(entry).readAllBytes();
                }
            }
        } else {
            // 开发环境，从文件系统读取
            Path path = Paths.get("resource").resolve(resourcePath);
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        }
        return null;
    }

    private String replacePath(String path, String packagePath) {
        return path.replace("src/demo/", "src/" + packagePath + "/");
    }

    private String replaceContent(String content, String packageName, String packagePath) {
        content = content.replace("demo.", packageName + ".");
        content = content.replace("package demo;", "package " + packageName + ";");
        content = content.replace("package demo.", "package " + packageName + ".");
        content = content.replace("demo.Controller", packageName + ".Controller");
        content = content.replace("demo.Model", packageName + ".Model");
        content = content.replace("controllerPackage(\"demo", "controllerPackage(\"" + packageName);
        return content;
    }

    private static final List<String> TEMPLATE_FILES = List.of(
            "src/demo/Controller/Home.java",
            "src/demo/Controller/User.java",
            "src/demo/Model/Greeter.java",
            "src/demo/Model/MUser.java",
            "src/demo/Main.java",
            "src/demo/TokenUtils.java",
            "configs/cfg.default.conf",
            "configs/cmap.default.conf",
            "build.sh",
            "run.sh"
    );

    /** TODO: 生成站点的启动脚本，单站点启动用 */
    public void genScriptRun() {

    }

    /** TODO: 生成站点的编译脚本，编译单站点用 */
    public void genScriptBuild() {

    }



    /** TODO: 检查当前目录下的所有站点目录的配置情况 */
    public List<String> checkDirs() {
        List<String> ok = new ArrayList<>();
        System.out.println("站点检查：" + home.toAbsolutePath().normalize());
        for (String name : siteNames()) {
            List<String> problems = problemsOf(name);
            if (problems.isEmpty()) {
                ok.add(name);
                System.out.printf("  %-24s OK   %s%n", name,
                        name.equals(DEFAULT_SITE) ? "(默认站点)" : hostsOf(name));
            } else {
                System.out.printf("  %-24s %d 个问题，详见 java -jar tinyweb.jar check %s%n",
                        name, problems.size(), name);
            }
        }
        for (String c : conflicts(ok)) System.out.println("  冲突: " + c);
        if (!ok.contains(DEFAULT_SITE)) {
            System.out.println("  缺少默认站点，多站点模式无法启动：java -jar tinyweb.jar gen-default");
        }
        return ok;
    }

    /** TODO: 检查特定站点的配置问题 */
    public List<String> reportSiteProblems(String name) {
        List<String> problems = problemsOf(name);
        if (problems.isEmpty()) {
            System.out.println(name + ": 没有发现问题，认领的 host: " + hostsOf(name));
        } else {
            System.out.println(name + ":");
            for (String p : problems) System.out.println("  - " + p);
        }
        return problems;
    }

    // ================= 检查规则 =================

    /** 一个站点能被载入的全部条件都在这里 */
    private List<String> problemsOf(String name) {
        List<String> p = new ArrayList<>();
        Path jar = jarOf(name);
        Path configs = home.resolve(name).resolve("configs");
        Path logs = home.resolve(name).resolve("logs");

        if (!Files.isRegularFile(jar)) p.add("缺少站点 jar: " + jar);
        if (!Files.isDirectory(configs)) {
            p.add("缺少配置目录: " + configs);
            return p;
        }
        if (!Files.isDirectory(logs)) p.add("缺少日志目录: " + logs);
        else if (!Files.isWritable(logs)) p.add("日志目录不可写: " + logs);

        List<String> hosts = hostsOf(name);
        if (name.equals(DEFAULT_SITE)) {
            for (String h : hosts) p.add("默认站点不能认领 host，请删除 cfg." + h + ".conf");
            if (!Files.isRegularFile(configs.resolve("cfg.default.conf"))) {
                p.add("缺少 cfg.default.conf");
            }
        } else if (hosts.isEmpty()) {
            p.add("没有认领任何 host：configs/ 下没有 cfg.<host>.conf");
        }

        // 每份生效的配置都要能找到控制器
        ConfigRegistry registry = new ConfigRegistry(configs, Map.of());
        for (String h : name.equals(DEFAULT_SITE) ? List.of("default") : hosts) {
            String pkg = registry.forHost(h).controllerPackage();
            if (pkg.isEmpty()) {
                p.add("cfg." + h + ".conf 缺少 app.controllerPackage");
            } else if (Files.isRegularFile(jar) && !jarHasPackage(jar, pkg)) {
                p.add("cfg." + h + ".conf 的 app.controllerPackage=" + pkg + " 在 " + jar + " 里找不到");
            }
        }
        return p;
    }

    /** 同一个 host 被多个站点认领 */
    private List<String> conflicts(List<String> names) {
        Map<String, List<String>> owners = new TreeMap<>();
        for (String name : names) {
            for (String h : hostsOf(name)) owners.computeIfAbsent(h, k -> new ArrayList<>()).add(name);
        }
        List<String> out = new ArrayList<>();
        owners.forEach((h, sites) -> {
            if (sites.size() > 1) out.add(h + " 被多个站点认领: " + String.join(", ", sites));
        });
        return out;
    }

    // ================= 目录约定 =================

    /** sites/ 下的站点 jar，加上当前目录下带 configs/ 的目录——缺哪一半都要报出来 */
    private List<String> siteNames() {
        TreeSet<String> names = new TreeSet<>();
        for (Path f : list(home.resolve("sites"))) {
            String n = f.getFileName().toString();
            if (n.startsWith(JAR_PREFIX) && n.endsWith(".jar")) {
                names.add(n.substring(JAR_PREFIX.length(), n.length() - ".jar".length()));
            }
        }
        for (Path d : list(home)) {
            if (Files.isDirectory(d.resolve("configs"))) names.add(d.getFileName().toString());
        }
        return new ArrayList<>(names);
    }

    private Path jarOf(String name) {
        return home.resolve("sites").resolve(JAR_PREFIX + name + ".jar");
    }

    /** configs/ 下 cfg.&lt;host&gt;.conf 的 host 部分，不含 cfg.default.conf */
    private List<String> hostsOf(String name) {
        List<String> hosts = new ArrayList<>();
        for (Path f : list(home.resolve(name).resolve("configs"))) {
            String n = f.getFileName().toString();
            if (!n.startsWith("cfg.") || !n.endsWith(".conf")) continue;
            String h = n.substring("cfg.".length(), n.length() - ".conf".length()).toLowerCase();
            if (!h.equals("default")) hosts.add(h);
        }
        hosts.sort(null);
        return hosts;
    }

    /** 与 ConfigRegistry.forHost 一致：去掉端口；另外转小写，Host 头大小写不敏感 */
    private static String hostOnly(String host) {
        int colon = host.indexOf(':');
        return (colon >= 0 ? host.substring(0, colon) : host).toLowerCase();
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean jarHasPackage(Path jar, String pkg) {
        String prefix = pkg.replace('.', '/') + "/";
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.stream().anyMatch(e -> e.getName().startsWith(prefix) && e.getName().endsWith(".class"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void appendStartupLog(Path logDir, List<String> lines) {
        StringBuilder sb = new StringBuilder("START " + java.time.LocalDateTime.now() + "\n");
        for (String l : lines) sb.append("  ").append(l).append('\n');
        try {
            Files.createDirectories(logDir);
            Files.writeString(logDir.resolve("startup." + LocalDate.now() + ".txt"), sb,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("写启动日志失败: " + logDir + " (" + e.getMessage() + ")");
        }
    }

    // ================= 生成 =================

    private static void writeIfMissing(Path file, String content) throws IOException {
        if (Files.exists(file)) {
            System.out.println("保留: " + file + "（已存在）");
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        System.out.println("生成: " + file);
    }

    /** 用运行中的 JDK 编译，classpath 就是 tinyweb.jar 自己 */
    private static void compileToJar(Path src, Path jar) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) throw new IllegalStateException("编译需要 JDK，当前运行的是 JRE");
        String self;
        try {
            self = Paths.get(Tools.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception e) {
            throw new IllegalStateException("找不到 tinyweb.jar 自身的位置", e);
        }

        Path classes = Files.createTempDirectory("tinyweb-gen");
        int rc = javac.run(null, null, null, "--release", "21", "-encoding", "UTF-8",
                "-cp", self, "-d", classes.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("编译失败: " + src);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> files = Files.walk(classes)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, out);
                out.closeEntry();
            }
        }
    }

    private static final String DEFAULT_CONFIG = """
            # 默认站点：接收没有 Host、或 Host 没被任何站点认领的请求。
            # 默认站点不能有 cfg.<host>.conf，只有这一份配置。
            app.controllerPackage=defaultsite
            # 扫描器和配错的请求不需要会话，关掉，免得会话表被它们撑大
            manualsession=1
            """;

    private static final String DEFAULT_CONTROLLER = """
            package defaultsite;

            import top.x0a.tinyweb.Controller;

            /**
             * 默认站点：所有没有 Host、或 Host 没被任何站点认领的请求都到这里。
             * nginx 漏了 proxy_set_header Host、有人拿 IP 直连、DNS 指错了机器……
             * 都会在 default/logs/ 里留下 BAD_HOST 记录。
             *
             * 由 java -jar tinyweb.jar gen-default 生成，可以改；改完再执行一次 gen-default 重新编译。
             */
            public class DefaultController extends Controller {
                @Override
                public void index() {
                    String host = ctx.host();
                    header("Content-Type", "text/plain; charset=utf-8");
                    if (host.isEmpty()) {
                        ctx.pushLog("BAD_HOST: (none)\\n");
                        status(400);
                        echo("错误的请求头：缺少 Host\\n");
                    } else {
                        ctx.pushLog("BAD_HOST: " + host + "\\n");
                        status(421);
                        echo("错误的请求头：Host 未登记\\n");
                    }
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Tools tools = new Tools(Paths.get(System.getProperty("tinyweb.home", ".")));
        String cmd = args.length > 0 ? args[0] : "";
        try {
            switch (cmd) {
                case "list" -> tools.checkDirs();
                case "check" -> {
                    if (args.length < 2) usage("check 需要站点名");
                    tools.reportSiteProblems(args[1]);
                }
                case "gen-default" -> tools.genDefaultSite();
                case "gen-project" -> {
                    if (args.length < 3) usage("gen-project 需要目标目录和包名");
                    tools.genProject(args[1], args[2]);
                    System.out.println("✓ 项目生成完成: " + args[1]);
                }
                default -> tools.runMultiSite(parsePort(
                        cmd.isEmpty() ? System.getProperty("tinyweb.port", "8080") : cmd));
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /** 端口写错就停下来说清楚，不悄悄换成 8080 */
    private static int parsePort(String s) {
        try {
            int port = Integer.parseInt(s.trim());
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignore) { }
        usage("无效的端口或命令: " + s);
        return -1;
    }

    private static void usage(String why) {
        System.err.println(why);
        System.err.println("""
                用法：
                  java -jar tinyweb.jar [port]                  启动多站点模式，端口缺省 8080
                  java -jar tinyweb.jar list                    列出站点及能否载入
                  java -jar tinyweb.jar check <name>            输出指定站点的配置问题
                  java -jar tinyweb.jar gen-default             生成默认站点
                  java -jar tinyweb.jar gen-project <dir> <pkg> 从 demo1 模板生成项目""");
        System.exit(2);
    }
}
