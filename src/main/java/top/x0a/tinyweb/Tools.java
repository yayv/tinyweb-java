package top.x0a.tinyweb;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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
 *  1. 生成默认站点, 接受无头请求，接受错误 &lt;域名:端口&gt; 请求头等
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

    /** 模板目录，结构与 demo1src 一一对应：src/ configs/ resource/static_pages/ build.gradle settings.gradle build.sh run.sh */
    private static final String TEMPLATE_BASE = "templates/demo1/";

    /**
     * 从 demo1 模板生成项目：模板目录下的文件全部带上，源码/配置/脚本替换包名，静态文件原样复制。
     * 目标文件已存在就保留不覆盖，重复执行不会冲掉已经改过的代码。
     */
    public Path genProject(String targetDir, String packageName) throws IOException {
        Path dir = Paths.get(targetDir);
        String packagePath = packageName.replace('.', '/');

        Map<String, byte[]> files = readTemplate();
        if (files.isEmpty()) throw new IllegalStateException("框架里找不到模板: " + TEMPLATE_BASE);

        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            Path target = dir.resolve(replacePath(path, packagePath));
            if (Files.exists(target)) {
                System.out.println("保留: " + target + "（已存在）");
                continue;
            }
            byte[] bytes = e.getValue();
            if (path.endsWith(".java") || path.endsWith(".conf") || path.endsWith(".sh")) {
                bytes = replaceContent(new String(bytes, StandardCharsets.UTF_8), packageName, packagePath)
                        .getBytes(StandardCharsets.UTF_8);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            if (path.endsWith(".sh")) target.toFile().setExecutable(true, false);   // run.sh 里是 ./build.sh
            System.out.println("生成: " + target);
        }

        // 生成的项目自带一份框架 jar，build.gradle / run.sh 先找 lib/tinyweb.jar，项目放到哪都能编译
        String self = selfLocation();
        Path lib = dir.resolve("lib/tinyweb.jar");
        if (!self.endsWith(".jar")) {
            System.out.println("跳过: " + lib + "（框架不是从 jar 运行的，请自己放一份）");
        } else if (Files.exists(lib)) {
            System.out.println("保留: " + lib + "（已存在）");
        } else {
            Files.createDirectories(lib.getParent());
            Files.copy(Paths.get(self), lib);
            System.out.println("复制: " + self + " -> " + lib);
        }
        return dir;
    }

    /** 框架自身所在位置：从 jar 运行时是 tinyweb.jar 的路径，开发时是 class 目录 */
    private static String selfLocation() throws IOException {
        try {
            return Paths.get(Tools.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("无法确定框架 jar 位置", e);
        }
    }

    /** 模板目录下的全部文件：相对路径 → 内容。从框架 jar 里读；开发时直接跑 class 目录则读 resource/ */
    private static Map<String, byte[]> readTemplate() throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        String self = selfLocation();
        if (self.endsWith(".jar")) {
            try (JarFile jar = new JarFile(self)) {
                for (JarEntry en : jar.stream().toList()) {
                    String name = en.getName();
                    if (en.isDirectory() || !name.startsWith(TEMPLATE_BASE) || name.endsWith(".DS_Store")) continue;
                    out.put(name.substring(TEMPLATE_BASE.length()), jar.getInputStream(en).readAllBytes());
                }
            }
        } else {
            Path root = Paths.get("resource").resolve(TEMPLATE_BASE);
            if (Files.isDirectory(root)) {
                try (Stream<Path> s = Files.walk(root)) {
                    for (Path f : s.filter(Files::isRegularFile).toList()) {
                        if (f.getFileName().toString().equals(".DS_Store")) continue;
                        out.put(root.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
                    }
                }
            }
        }
        return out;
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
            # 按 Java properties 规则解析：反斜杠是转义符，Windows 路径写成 C:\\\\logs\\\\tinyweb
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

    // ================= 运行环境检查 =================

    /** 已验证可用的 Gradle 版本；低于 8.5 的 Gradle 跑不起 Java 21 */
    private static final String GRADLE_VERSION = "9.7.1";
    private static final int[] GRADLE_MIN = {8, 5};

    /**
     * 检查编译/运行业务项目需要的外部依赖，缺什么就给出安装方法。
     * 能执行到这里说明 JVM 至少是 21（本 jar 按 --release 21 编译，更低的版本连 main 都进不来）。
     * @return 全部满足为 true
     */
    public boolean checkEnv() {
        boolean win = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        boolean mac = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
        boolean ok = true;
        System.out.println("运行环境检查：");

        System.out.printf("  %-8s OK   %s (%s)%n", "java", Runtime.version(), System.getProperty("java.home"));

        // 查命令行上的 javac 而不是当前 JVM：build.sh / gradle 用的是 PATH 上的工具
        Path javac = which("javac", win);
        String javacVersion = javac == null ? null : versionLine(javac, "-version", "javac ");
        if (javac == null) {
            ok = false;
            System.out.printf("  %-8s 缺失 PATH 里找不到 javac，只装了 JRE 不能编译，需要 JDK 21+%n", "javac");
            System.out.println("           下载: https://adoptium.net/temurin/releases/?version=21");
        } else if (javacVersion == null) {
            ok = false;
            System.out.printf("  %-8s 不可用 %s 执行 javac -version 失败（macOS 没装 JDK 时 /usr/bin/javac 只是个空壳）%n", "javac", javac);
            System.out.println("           下载: https://adoptium.net/temurin/releases/?version=21");
        } else if (versionAtLeast(javacVersion, new int[]{21})) {
            System.out.printf("  %-8s OK   %s (%s)%n", "javac", javacVersion, javac);
        } else {
            ok = false;
            System.out.printf("  %-8s 过旧 %s (%s)，需要 JDK 21+%n", "javac", javacVersion, javac);
            System.out.println("           下载: https://adoptium.net/temurin/releases/?version=21");
        }

        Path gradlew = home.resolve(win ? "gradlew.bat" : "gradlew");
        Path gradle = which("gradle", win);
        if (Files.isRegularFile(gradlew)) {
            System.out.printf("  %-8s OK   用项目自带的 wrapper: %s（首次运行会自动下载 Gradle）%n", "gradle", gradlew);
        } else if (gradle == null) {
            ok = false;
            System.out.printf("  %-8s 缺失 PATH 里找不到 gradle%n", "gradle");
            gradleInstallHint(win, mac);
        } else {
            String v = versionLine(gradle, "--version", "Gradle ");
            if (v == null) {
                System.out.printf("  %-8s ?    %s 取不到版本号，请手动执行 gradle --version 确认%n", "gradle", gradle);
            } else if (versionAtLeast(v, GRADLE_MIN)) {
                System.out.printf("  %-8s OK   %s (%s)%n", "gradle", v, gradle);
            } else {
                ok = false;
                System.out.printf("  %-8s 过旧 %s (%s)，Java 21 需要 Gradle %d.%d+%n", "gradle", v, gradle, GRADLE_MIN[0], GRADLE_MIN[1]);
                gradleInstallHint(win, mac);
            }
        }

        if (win) {
            if (which("bash", true) != null) {
                System.out.printf("  %-8s OK%n", "bash");
            } else {
                ok = false;
                System.out.printf("  %-8s 缺失 build.sh / run.sh 需要 bash%n", "bash");
                System.out.println("           安装 Git for Windows（自带 Git Bash）: https://git-scm.com/download/win");
            }
        }

        System.out.println(ok ? "环境齐全。" : "有缺项，按上面的提示安装后再执行一次 check-env。");
        return ok;
    }

    private static void gradleInstallHint(boolean win, boolean mac) {
        String zip = "gradle-" + GRADLE_VERSION + "-bin.zip";
        System.out.println("           安装方法任选其一：");
        if (mac) {
            System.out.println("             brew install gradle");
        } else if (win) {
            System.out.println("             scoop install gradle      或   choco install gradle");
        } else {
            System.out.println("             curl -s \"https://get.sdkman.io\" | bash && sdk install gradle " + GRADLE_VERSION);
        }
        System.out.println("             手动下载解压，把 bin 目录加进 PATH:");
        System.out.println("               https://services.gradle.org/distributions/" + zip);
        System.out.println("               国内镜像: https://mirrors.cloud.tencent.com/gradle/" + zip);
        System.out.println("             全部版本: https://gradle.org/releases/");
    }

    /** 在 PATH 里找可执行文件；Windows 下依次试 .bat/.cmd/.exe */
    private static Path which(String name, boolean win) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        List<String> names = win ? List.of(name + ".bat", name + ".cmd", name + ".exe") : List.of(name);
        for (String dir : path.split(java.io.File.pathSeparator)) {
            for (String n : names) {
                try {
                    Path p = Paths.get(dir, n);
                    if (Files.isRegularFile(p) && Files.isExecutable(p)) return p;
                } catch (java.nio.file.InvalidPathException ignore) { }
            }
        }
        return null;
    }

    /** 执行 `exe flag`，取以 prefix 开头那一行去掉 prefix 的部分（如 "javac 21.0.11" → "21.0.11"）；失败返回 null */
    private static String versionLine(Path exe, String flag, String prefix) {
        try {
            Process p = new ProcessBuilder(exe.toString(), flag).redirectErrorStream(true).start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            for (String line : out.split("\\R")) {
                if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** "9.7.1" / "8.5-rc-1" 这类版本号是否不低于 min */
    private static boolean versionAtLeast(String version, int[] min) {
        String[] parts = version.split("[.\\-]");
        for (int i = 0; i < min.length; i++) {
            int v;
            try {
                v = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            } catch (NumberFormatException e) {
                v = 0;
            }
            if (v != min[i]) return v > min[i];
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        Tools tools = new Tools(Paths.get(System.getProperty("tinyweb.home", ".")));
        String cmd = args.length > 0 ? args[0] : "";
        String port = args.length > 1 ? args[1] : "";
        try {
            switch (cmd) {
                case "list" -> tools.checkDirs();
                case "check" -> {
                    if (args.length < 2) usage("check 需要站点名");
                    tools.reportSiteProblems(args[1]);
                }
                case "gen-default" -> tools.genDefaultSite();
                case "check-env" -> {
                    if (!tools.checkEnv()) System.exit(1);
                }
                case "gen-project" -> {
                    if (args.length < 3) usage("gen-project 需要目标目录和包名");
                    tools.genProject(args[1], args[2]);
                    System.out.println("✓ 项目生成完成: " + args[1]);
                }
                case "run-sites" -> tools.runMultiSite(parsePort(
                        port.isEmpty() ? System.getProperty("tinyweb.port", "8080") : port));
                default ->  usage("");
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
                  java -jar tinyweb.jar [usage]                 打印帮助信息（本提示）
                  java -jar tinyweb.jar list [dir]              列出指定目录下的站点及能否载入
                  java -jar tinyweb.jar run-sites [port]        启动多站点模式，端口缺省 8080
                  java -jar tinyweb.jar check <name>            输出指定站点的配置问题
                  java -jar tinyweb.jar check-env               检查 JDK / Gradle 等运行环境，缺什么给出安装方法
                  java -jar tinyweb.jar gen-project [dir]       生成默认项目 不指定 div 和 pkg 参数时, dir 默认为 default
        """);
        System.exit(2);
    }
}
