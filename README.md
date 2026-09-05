# tinyWEB-java

`tinycake-php` 的 Java 21 移植：一个可以打成 **jar 被业务项目 import** 的最小 Web 框架，
自带端口监听（虚拟线程每连接）。零依赖，只需 JDK 21+。

```
tinyWEB-java/
├── build.sh                        只编译框架 -> dist/tinyweb.jar
├── src/main/java/top/x0a/tinyweb/ 框架源码（jar 的全部内容）
└── demo/                           业务示例项目：只依赖 tinyweb.jar
    ├── build.sh                    javac -cp ../dist/tinyweb.jar
    ├── run.sh
    ├── configs/                    cfg.*.conf / cmap.*.conf
    └── src/demo/                   Home / Greeter / Main
```

## 快速开始

```bash
./build.sh          # -> dist/tinyweb.jar
./demo/run.sh 8080  # 编译 demo 并起服务
curl http://localhost:8080/home/index
```

## 业务项目怎么用

Java 没有 PHP `include` 一下就活的语义，phar 的等价物在这里是：**jar 给基类 + 启动器，
业务只写子类**。两种起法：

**A. 三行 Main（推荐，配置全在代码里）**

```java
import top.x0a.tinyweb.TinyWeb;

public class Main {
    public static void main(String[] args) throws Exception {
        TinyWeb.builder()
                .home(".")                    // configs/ 与 logs/ 的父目录
                .controllerPackage("com.x.c")
                .listen(8080)
                .run();
    }
}
```

**B. 一行 Java 都不写**（配置放 `configs/cfg.default.conf`）

```bash
java -Dtinyweb.home=. -Dtinyweb.controllerPackage=com.x.c \
     -cp app.jar:tinyweb.jar top.x0a.tinyweb.Bootstrap 8080
```

控制器：

```java
public class Home extends Controller {
    @Override public void index() { echo("<h1>hi</h1>"); }

    public void api()  { status(201); json("{\"ok\":true}"); }
    public void old()  { redirect("/home/index"); }
    public void who()  { echo(getModel(User.class).name(ctx.param("id"))); }
}
```

## 公开 API 面

jar 里只有这些是 `public`，其余（`HttpServer` / `FrontController` / `ConfigRegistry` /
`UrlParser` / `SessionStore`）是包内可见，业务代码编译期就够不着：

| 类型 | 用途 |
|------|------|
| `TinyWeb` + `TinyWeb.Builder` | 启动器，等价于 phar stub |
| `Bootstrap` | jar 的 Main-Class，`-D` 系统属性外壳 |
| `Controller` | 控制器基类：`echo` / `status` / `header` / `json` / `redirect` / `getModel` / `missing` |
| `Model` | 模型基类：`pushError` / `popError` |
| `Context` | 请求级上下文：请求读取 + 响应构造 + 日志（写入器全部包内可见） |
| `Config` | 当前 host 的只读配置：`getConfig` / `getString` / `getAllConfig` |
| `Session` | 会话数据袋：`id` / `get` / `put` / `remove` / `data` |
| `DefaultController` | 兜底控制器 |

`Context` 的请求侧：`host() method() uri() body() header() param() params() form()
controller() action() session()`；
响应侧：`write() status() contentType() setHeader() cookie() json() redirect()`。

## 路由

两种模式（对照 PHP 的 rewrite / index.php 两分支）：

- **路径**：`/controller/action/key1-value1/key2-value2`，每段还会以 `params0/params1/...`
  进 `$_GET` 等价物；`sitebase` 的路径部分会被先剥掉；`cmap.<host>.conf` 可重映射
  （`c/a = C/a` 或 `c = C`）。
- **query**：`/index.php?controller=a&action=b`。

可当 action 的只有业务子类自己声明的 `public` 无参方法；框架基类成员一律 `protected`，
不会被 URL 直接调到。控制器按名反射加载前有三道闸：名字须匹配 `[A-Za-z][A-Za-z0-9_]*`、
限定在 `app.controllerPackage` 前缀下、必须是 `Controller` 的具体子类。

## 配置

`configs/cfg.<host>.conf`（`key=value`，`#` 注释，`key.subkey=value` 两级嵌套，
缺文件回退 `cfg.default.conf`）：

```
app.port=8080
app.controllerPackage=demo
# app.sessionCookie=TCSESSID
sitebase=http://localhost:8080/
# manualsession=1
# clicklog=/var/log/tinyweb
```

优先级：`TinyWeb.Builder` 的代码级覆盖 > 配置文件。端口另有一层：
`listen()` / 命令行参数 > `app.port` > 8080。

## 模块对照（PHP → Java）

| PHP | Java | 说明 |
|-----|------|------|
| `Core`（每请求单例） | `ConfigRegistry` + `Context` | 进程级只读配置 vs 请求级状态，拆开 |
| `core.php::loadConfig` | `ConfigRegistry.forHost` | host 懒加载 + 缓存，缺省回退 default |
| `core.php::ControllerMap` | `Config.controllerMap` | 三分支路由映射 |
| `core.php::rebuildUrl` | `UrlParser.rebuild` | URL 段解析成 controller/action/method + params |
| `_log`/`_callstack`/`shutdown` | `Context` 同名方法 | 请求级日志与 callstack 校验 |
| `controller.php` | `Controller` | 抽象基类 + getModel |
| `mo`/`emptymodel` 透明代理 | 丢弃 | 见下方取舍 |
| `model.php` | `Model` | `static $_error` 下沉到 Context |
| `web_index.php` | `FrontController.handle` | 派发流水线 |
| `session_start()` | `SessionStore` + `Session` | 内存存储 + Set-Cookie + 空闲过期 |
| （无，靠 FastCGI/FPM） | `HttpServer` | **新增**：端口监听 + 虚拟线程每连接 |
| （无） | `TinyWeb` | **新增**：构建器式启动 API |

## 与 PHP 的有意分歧

1. **Core 拆两半**：`_config` 提升为进程级 `ConfigRegistry`（host 缓存），`_log`/`_callstack`/
   请求参数留在请求级 `Context`。PHP 每请求进程隔离，长驻进程必须显式拆。
2. **`model::$_error` static → Context**：多线程长驻里 static 会跨请求串数据 + 竞态。
3. **`mo` 透明代理丢弃**：它做计时日志 + 抓意外输出；Java 模型不 echo，抓输出失效，
   计时不值得引字节码库做反射代理（破坏零依赖）。
4. **`getModel(String)` 与 `EmptyModel` 一并删除**：静态类型下"模型不存在就返回空壳、
   让调用方自己判空"既不透明也不安全。只保留 `getModel(Class)`，模型缺失是编译期错误。
   `app.modelPackage` 随之作废。
5. **`echo` → `Context.write`**：控制器写响应缓冲，不直写 stdout。
6. **端口监听 + 轮询**：`ServerSocket.accept()` 阻塞轮询 + `newVirtualThreadPerTaskExecutor`
   每连接一个虚拟线程。不用 NIO Selector——它与"每请求独立上下文"的模型相冲，
   而 Loom 让阻塞写法同样高吞吐。
7. **会话 cookie 名 `PHPSESSID` → `TCSESSID`**（`app.sessionCookie` 可改）。
8. **`initalize` → `initialize`**：更正 PHP 拼写笔误。
9. **配置文件名不带端口**：PHP 把 `HTTP_HOST` 的冒号换成下划线（`example.com:8080`
   → `cfg.example.com_8080.conf`）；这里直接截掉端口（→ `cfg.example.com.conf`）。
   因为一个进程只监听一个端口（端口由 `listen()` / 命令行 / `app.port` 决定），
   不存在"同进程不同端口并存"的情况，端口段对配置选择没有信息量。

## 已知边界

- 只解析最小 HTTP/1.1：请求行 + 头 + `Content-Length` body，逐连接 `close`，
  无 keep-alive、无 chunked、无 multipart（文件上传要自己在 `ctx.body()` 上做）。
- 会话是单进程内存存储，不跨实例；要多实例请替换 `SessionStore`（`Session` 接口不变）。
- 无模板引擎、无数据库层（`Controller._db` 是留给你注入的占位）。
- 未知控制器回退 `DefaultController` 并返回 200，不是 404——保持与 PHP 版一致。

## libstrapper

被移植的四个文件里没有 libstrapper 调用（它是参数校验层，要到具体 controller/model
校验入参时才进来）。等 `libstrapper-java` 就位后，在业务 controller/model 里直接调用即可。
