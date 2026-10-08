
tinyweb-java.jar 用法

当前文件读取流程

HTTP 请求
  ↓
/static/index?f=showDemo
  ↓
Static 控制器
  ↓
JarFileServe 模型
  ↓
jar 包内的 static_pages/showDemo.html
  ↓
返回 HTTP 响应

项目生成流程

java -jar tinyweb.jar gen-project /path/to/project com.example.app
  ↓
Tools.genProject()
  ↓
readTemplateFile() 从 jar 包读取 templates/demo1/...
  ↓
替换包名、写到目标目录

现在静态文件和模板都通过 jar 包内的资源读取，不需要解压，更加优雅高效。


# tinyWEB.jar 使用文档

> 版本：tinyweb.jar 0.2.0（`build.sh`）· JDK 21

---

## 1. 它是什么
tinyweb.jar 是一个能打成 **jar 被业务项目 import** 的最小 Web 框架，自带端口监听（每连接一个虚拟线程），零第三方依赖微型MVC结构的Web服务开发框架，只需 JDK 21+。

设计是 **MVC + 约定路由**：

- **Controller**（控制器）子类，里面的每个 `public` 无参方法就是一个可被 URL 调用的动作（action）。
- **Model**（模型）子类，承接业务/数据逻辑。
- 框架负责：监听端口 → 解析 HTTP → 按 URL 找到控制器和方法 → 反射调用 → 把你写进响应缓冲的内容发回去。
- 可以由项目入口程序单独启动服务，是单站点模式，也可以由框架 tinyweb.jar 启动服务同时支持多个站点。

**框架不含**：模板引擎、数据库层、JSON 序列化库、文件上传解析。这些按需自己接（见 §9 已知边界）。

---

## 2. 心智模型：jar 给基类，业务只写子类

> **jar 提供 `Controller` / `Model` 基类 + `TinyWeb` 启动器，你的项目只写子类，启动时三行代码。**

一次请求的生命周期：

```
HTTP 请求
  ↓  HttpServer 解析请求行 / 头 / body，query string 进 params
  ↓  按 Host 头选配置（cfg.<host>.conf，缺则回退 default）
  ↓  自动开会话（除非 manualsession=1）
  ↓  解析 URL → controller / action（两种模式见 §6）
  ↓  cmap 重映射 → 反射加载控制器类 → 找 action 方法
  ↓  调用 action，你在方法里 echo(...) / json(...) / status(...) 写响应
  ↓  HttpServer 把 Context 上攒的状态码/头/cookie/正文写回，连接 close
```

每个请求一个独立的 `Context` 实例，天然线程隔离——所以业务代码里**不要用 static 存请求态**。

---

## 3. 快速开始（构建框架 jar）

```bash
cd tinyweb-java
./build.sh            # 编译框架 → dist/tinyweb.jar
```

`build.sh` 只编译框架本身，产物是 `dist/tinyweb.jar`（Main-Class 是内置命令行工具 `top.x0a.tinyweb.Tools`）。
业务项目**不在**这一步编译——它把这个 jar 放进 classpath 单独编译。

---

## 4. 建一个业务项目

有两条路：**A. 自己写三行 Main（开发推荐）**，**B. 用脚手架生成**。

### 4A. 自己写三行 Main（推荐）

目录约定（与 `demo1src/` 一致）：

```
myapp/
├── lib/tinyweb.jar              框架 jar（从 dist/ 拷来）
├── configs/
│   ├── cfg.default.conf         站点配置（见 §7）
│   └── cmap.default.conf        路由映射（可留空）
├── logs/                        运行时日志落盘目录（自动创建）
├── resource/                    静态资源（可选）
└── src/
    └── com/example/app/
        ├── Main.java            入口
        ├── Controller/          控制器包
        │   └── Home.java
        └── Model/               模型包
            └── ...
```

入口 `Main.java`：

```java
package com.example.app;

import top.x0a.tinyweb.TinyWeb;

public final class Main {
    public static void main(String[] args) throws Exception {
        TinyWeb.site()                                   // 注意：入口方法是 site()
                .home(System.getProperty("tinyweb.home", "."))   // configs/ 与 logs/ 的父目录
                .controllerPackage("com.example.app.Controller") // 控制器所在包
                .listen(args.length > 0 ? Integer.parseInt(args[0]) : 8080)
                .run();                                  // 构建并阻塞运行
    }
}
```

> ⚠️ 入口是 `TinyWeb.site()`，不是 `TinyWeb.builder()`。项目里旧的 README / 注释可能还写着
> `builder()` 和一个叫 `Bootstrap` 的类——那两个名字在当前代码里都不存在，以本文档和 `src/` 为准。

编译并运行：

```bash
# 编译业务代码到 out/，classpath 放框架 jar
javac -cp lib/tinyweb.jar -d out $(find src -name '*.java')

# 运行：out 和 jar 都要在 classpath 上；tinyweb.home 指向 configs/ 与 logs/ 的父目录
java -Dtinyweb.home=. -cp out:lib/tinyweb.jar com.example.app.Main 8080

curl http://localhost:8080/home/index
```

### 4B. 用脚手架生成一个项目

从内置的 demo1 模板生成一套完整骨架（Main + 示例控制器/模型 + 配置 + 构建脚本），并把框架 jar 拷进 `lib/`：

```bash
java -jar dist/tinyweb.jar gen-project ./myapp com.example.app
```

- 两个参数**都必填**：目标目录、业务包名。
- 已存在的文件**保留不覆盖**，可以重复执行而不冲掉你改过的代码。
- 生成后进目录 `./build.sh && ./run.sh 8080` 即可（脚本随模板一起生成）。

---

## 5. 写控制器和模型

### 控制器

```java
package com.example.app.Controller;

import top.x0a.tinyweb.Controller;
import com.example.app.Model.MUser;

public class Home extends Controller {

    // index() 是必须实现的抽象方法：/home 或 /home/index 时调用
    @Override
    public void index() {
        echo("<h1>hi</h1>");
    }

    // 任意 public 无参方法都是一个 action：/home/api
    public void api() {
        status(201);
        json("{\"ok\":true}");            // 框架零依赖，JSON 字符串自己拼/序列化
    }

    // /home/old → 302 跳转
    public void old() {
        redirect("/home/index");
    }

    // 读参数 + 用模型
    public void who() {
        String id = ctx.param("id");      // 路径段 id-xxx 或 ?id=xxx
        MUser u = getModel(MUser.class);  // 类型安全地拿模型实例
        echo(u.name(id));
    }
}
```

控制器基类提供的方法（都是 `protected`，在你的子类里直接调）：
`echo` / `status` / `header` / `json` / `redirect` / `getModel(Class)` / `missing`；
以及两个字段 `ctx`（请求级上下文，读参数/写响应/会话都在它上面）和 `config`（当前 host 的只读配置）。
完整清单见[接口文档](./接口文档.md#controller)。

**什么能当 action：** 只有你子类里**自己声明的 `public` 无参方法**。框架基类的方法一律 `protected`，
不会被 URL 直接调到；`Object` 的方法（如 `toString`）也不会。

### 模型

```java
package com.example.app.Model;

import top.x0a.tinyweb.Model;

public class MUser extends Model {
    public boolean validate(String user, String pass) {
        if (user == null || user.isBlank()) {
            pushError("username", "用户名不能为空");   // 错误进请求级错误栈
            return false;
        }
        return true;
    }
}
```

模型必须有一个 `public` 无参构造（`getModel` 用反射 `newInstance`）。
模型里 `pushError(params, msg)` 压栈，控制器里 `ctx.popError()` 取出——错误栈是**请求级**的
（PHP 版的 `static $_error` 在长驻多线程里会串数据，这里下沉到了 `Context`）。

---

## 6. 路由

两种模式并存，对照 PHP 的 rewrite / index.php 两分支：

### 路径模式（默认）

```
/controller/action/key1-value1/key2-value2
```

- 第 0 段 → 控制器，第 1 段 → action，第 2 段 → method（保留）。
- **每一段**都会以 `params0` / `params1` / ... 存进参数表；含 `-` 的段还会拆成 `key=value` 存进去
  （`id-42` → `ctx.param("id")` 得到 `"42"`，同时 `ctx.param("params2")` 得到 `"id-42"`）。
- 控制器类名 = 段名**首字母大写**后，在 `app.controllerPackage` 包下查找（`home` → `com.example.app.Controller.Home`）。
- `sitebase` 配置里的**路径部分**会先从 URI 前缀剥掉。

例：`GET /user/login` → 控制器 `User`、action `login`。

### query 模式

```
/index.php?controller=user&action=login&method=x
```

query string 会被解析进参数表；只要带了 `?controller=...`，就走这条分支（`action` 缺省 `index`）。

### 路由映射 cmap

`configs/cmap.<host>.conf` 可重映射（缺文件或留空则走默认解析）：

```
# c/a = C/a   把 blog/show 映射到 Article/detail
blog/show = Article/detail
# c = C       把整个 blog 控制器映射到 Article（action 固定为 index）
blog = Article
```

### 加载三道闸 & 兜底

反射加载控制器前有三道约束：① 名字须匹配 `[A-Za-z][A-Za-z0-9_]*`；② 限定在 `app.controllerPackage` 前缀下；
③ 必须是 `Controller` 的**具体**子类。任一不满足 → 回退 `DefaultController`。

- **未知控制器**：回退 `DefaultController` 并返回 **200**（不是 404）——与 PHP 版一致。
- **未知 action**：回退到该控制器的 `index()`。
- **action 抛异常**：返回 **500**，并在日志记 `CallStack is NOT empty`。

---

## 7. 配置

`configs/cfg.<host>.conf`，按 **Java `Properties` 规则**解析（UTF-8，中文直接写不必转义；
`\` 是转义符，Windows 路径要写两个反斜杠 `C:\\logs`）。`#` 开头是注释。

```properties
app.port=8080
app.controllerPackage=com.example.app.Controller
# app.sessionCookie=TCSESSID     # 会话 cookie 名，缺省 TCSESSID
sitebase=http://localhost:8080/
# manualsession=1                # 置 1 则框架不自动开会话
# clicklog=/var/log/tinyweb      # 非空则每次请求记一条 clicklog
```

| 配置项 | 作用 | 缺省 |
|--------|------|------|
| `app.port` | 监听端口 | 8080 |
| `app.controllerPackage` | 业务控制器所在包（反射加载被限制在此前缀下） | 空 |
| `app.sessionCookie` | 会话 cookie 名 | `TCSESSID` |
| `sitebase` | 站点根 URL，其**路径部分**会从 URI 前缀剥掉 | — |
| `manualsession` | 置 `1`/`true` 则框架不自动开会话 | 关 |
| `clicklog` | 非空则记录 clicklog | — |

**两级嵌套**：`key.subkey=value` 会被解析成两级（`app.port` → `app` 下的 `port`）。
读取用 `config.getConfig("app", "port")`。

**host 选择与回退**：用 HTTP `Host` 头（端口部分会被截掉，`example.com:8080` → `example.com`）选
`cfg.example.com.conf`；文件不存在则回退 `cfg.default.conf`。

**优先级**：
- 配置项：`TinyWeb.Builder` 的代码级覆盖（`.config()` / `.controllerPackage()`）**>** 配置文件。
  所以「三行 Main」不摆 configs 目录也能起。
- 端口：`listen()` / 命令行参数 **>** `cfg.default.conf` 的 `app.port` **>** 8080。

---

## 8. 会话（Session）

- 默认**自动开启**：首次请求签发一个 `SecureRandom` 随机 id，下发 cookie
  `TCSESSID=<id>; Path=/; HttpOnly; SameSite=Lax`。
- 存储在**单进程内存**里，默认空闲 **30 分钟**过期（`sessionTimeout(Duration)` 可改）。
- 客户端带来的未知/已过期 id 不会被采信，会重新签发（防 session fixation）。
- 用法：`ctx.session().put("uid", 7)` / `ctx.session().get("uid")`。
- `manualsession=1` 时框架不开会话，`ctx.session()` 返回 `null`。

> ⚠️ 会话是单进程内存，**不跨实例**。多实例部署要替换存储（见 §9）。

---

## 9. 已知边界（建站前必读）

- **最小 HTTP/1.1**：只解析请求行 + 头 + `Content-Length` body，逐连接 `close`。
  **无** keep-alive、**无** chunked、**无** multipart。文件上传要自己在 `ctx.body()` 上解析。
- **表单**：`ctx.form(k)` 只在 `Content-Type: application/x-www-form-urlencoded` 时有值。
  JSON 请求体请读 `ctx.body()` 自己解析。
- **大小上限**：请求头 ≤ 16KB，body ≤ 8MB，socket 读超时 15s。
- **无 JSON 库**：`json(...)` 只是设 `Content-Type` 并把你给的字符串当正文。序列化自理。
- **无数据库层**：`Controller._db` 是留给你注入的占位符（当前框架不填充它）。
- **会话不跨实例**：换 Redis/文件存储需替换 `SessionStore`（`Session` 接口不变）。
- **未知控制器返回 200**（走 `DefaultController`），不是 404——如需 404 语义，自己在控制器里判。

---

## 10. 部署

### 开发 / 单站点

就是 §4A 那条命令：你自己的 `Main` + `TinyWeb.site()`。

```bash
java -Dtinyweb.home=. -cp out:lib/tinyweb.jar com.example.app.Main 8080
```

### 生产 / 单进程多站点

用 jar 内置的多站点启动器，按 HTTP `Host` 头把请求分给不同站点（每个站点独立 classloader、
独立 configs / logs）：

```bash
# 部署目录结构：
# deploy/
#   ├── default/           必须有默认站点，先生成它
#   │   ├── configs/  logs/
#   ├── sites/<name>.jar   各站点编译产物
#   └── <name>/configs/    各站点配置（cfg.<host>.conf 声明它认领哪些 Host）

cd deploy
java -jar tinyweb.jar gen-default        # 生成必需的默认站点
java -jar tinyweb.jar list               # 列出站点及能否载入
java -jar tinyweb.jar check <name>       # 输出某站点的配置问题
java -jar tinyweb.jar run-sites 8080     # 启动多站点，未登记的 Host 落到 default
```

未被任何站点认领的 Host 会落到 `default` 站点；同一个 Host 被多站点认领则拒绝启动。

---

## 11. 命令行速查（`java -jar tinyweb.jar <cmd>`）

| 命令 | 作用 |
|------|------|
| `usage` | 打印帮助 |
| `list [dir]` | 列出目录下的站点及能否载入 |
| `run-sites [port]` | 启动多站点模式（端口缺省 8080，也可用 `-Dtinyweb.port`） |
| `check <name>` | 输出指定站点的配置问题 |
| `check-env` | 检查 JDK / Gradle 等运行环境 |
| `gen-project <dir> <package>` | 从 demo1 模板生成业务项目骨架并拷入框架 jar |
| `gen-default` | 生成多站点必需的默认站点 |

`-Dtinyweb.home=<dir>` 指定工作目录（默认当前目录）。

---

## 12. 参考：5 个 demo

`tinyweb-java/` 下的 `demo1src`~`demo5src` 是可直接编译运行的完整业务项目，是最好的活文档：

| demo | 主题 |
|------|------|
| demo1 | 无数据库的 JWT 登录 / 退出 / 身份验证 |
| demo2 | 直连数据库的 CRUD 网站 |
| demo3 | 简单的无头 CMS（前端见 `vue3-pcweb-cms`） |
| demo4 | 简单的无头电商（无支付，前端见 `vue3-pcweb-eshop`） |
| demo5 | 权限控制 |

建你的第一个站时，`demo1src` 的 `Main.java` / `Controller/` / `Model/` / `configs/` 可直接照抄改名。




