# tinyWEB-java 待补清单

一个"开箱即用的极简 Java Web 框架"还缺什么。按**能不能上生产**分档，不按功能罗列。

前提（已定，贯穿全文）：

- **零依赖只约束框架**，不约束站。站 jar 可以自带 MyBatis、Jackson、任何东西，各站互不干扰。
- **永远有反向代理在前**（nginx）。TLS、HTTP/2、静态文件、压缩、限流都是它的活。
- **API 优先，不做模板引擎**。PHP 版的 `templates/` 是早年 Smarty 的遗留，现已全走 API。

---

## 一、缺了就不能上生产（四条）

### 1. 优雅停机

**现状**：没有。systemd `restart` 发 SIGTERM，进程当场死，**在途请求全部静默丢弃**。每次发版掉一批请求。

#### 背景：为什么 Java 没有析构函数

这不是遗漏，是一个被验证过的失败设计被主动撤掉了。理解这一点，MyBatis 之类的资源该怎么关就是顺推的结论。

**根因是 GC 让对象的死亡时刻不确定。** C++ 的析构函数能成立，是因为对象要么在栈上（离开作用域即销毁），
要么由 `delete` 显式销毁——**时刻是确定的**。Java 的对象由 GC 回收，而 GC 什么时候跑、跑不跑，
取决于内存压力和收集器实现。所以"对象死的时候自动清理"这个语义，在 Java 里根本没有可依赖的触发点。

早期 Java 还是试了，就是 `Object.finalize()`。它失败得很彻底：

| 问题 | 后果 |
|---|---|
| 不保证被调用 | JVM 先退出就永远不跑，"清理"是薛定谔的 |
| 调用时机不确定 | 文件句柄可能几分钟后才关，高并发下直接耗尽 fd |
| 无顺序保证、线程不确定 | 依赖关系无法表达 |
| 可以"复活"对象 | 在 `finalize` 里把 `this` 赋给静态字段，对象重新可达——GC 的不变式被破坏 |
| 异常被静默吞掉 | 清理失败了你不知道 |
| 拖慢 GC | 可终结对象至少要过两轮 GC 才能回收 |
| 安全漏洞面 | 攻击者子类化并重写 `finalize`，可拿到构造失败的半成品对象 |

结论：JDK 9 标记废弃 → **JEP 421（JDK 18）默认关闭** → 计划移除。你的 JDK 21 上它默认就是不生效的。

**替代方案是把"自动"换成"显式"，并按作用域分三层：**

| 层级 | 机制 | 用于 | 时机 |
|---|---|---|---|
| 语句块 | `try-with-resources` + `AutoCloseable` | 连接、流、锁 | 块退出，**确定** |
| 对象（兜底） | `java.lang.ref.Cleaner`（JDK 9+） | 包装本地内存的对象 | GC 之后，**不保证**，只当安全网 |
| 进程 | `Runtime.addShutdownHook` | 连接池、线程池、日志 handle | 进程正常退出，**尽力而为** |

这就是整个设计思路的转变：**Java 放弃了"对象死了自动清理"，改成"你说什么时候清理，就什么时候清理"。**
所以框架必须显式提供"站要停了"的回调（第 2 条的 `Index.stop()`）——没有任何机制会替你调用它。

#### 机制：一个钩子覆盖所有可捕获的退出

不需要手动捕获信号（`sun.misc.Signal` 是内部 API，不要碰）：

```java
Runtime.getRuntime().addShutdownHook(new Thread(this::gracefulStop, "tinyweb-shutdown"));
```

一个钩子同时覆盖：

| 信号/事件 | 来源 | 触发 |
|---|---|---|
| SIGINT | Ctrl-C | ✅ |
| SIGTERM | `systemctl stop` / `docker stop` / `kill` | ✅ |
| SIGHUP | 终端断开 | ✅ |
| `System.exit()` / main 返回 | 正常退出 | ✅ |
| SIGKILL (`kill -9`) | 强杀 | ❌ 任何方案都收不到 |
| JVM 硬崩 / 断电 | — | ❌ |

#### 钩子的三条语义，决定了代码该怎么写

1. **多个钩子是并发启动的，彼此无顺序保证。** JVM 把每个钩子当独立线程 start 起来，然后等全部结束。
   所以**只注册一个钩子**，顺序在它内部自己排——注册三个然后指望它们按注册顺序跑，是错的。
2. **钩子里不能依赖其他守护线程还活着。** 日志的异步写线程、定时任务线程可能已经在收尾。
   （这也是前面建议日志用同步写而非异步队列的理由之一：停机时不必担心队列里的东西丢了。）
3. **钩子自己会阻塞退出。** JVM 会一直等到钩子返回。所以钩子内部的每一步都必须有超时，
   否则一个卡住的连接会让进程永远停不掉——直到 systemd 失去耐心 SIGKILL。

#### 停机顺序

```java
private void gracefulStop() {
    // 1. 停止接受新连接。已建立的连接不受影响。
    //    nginx 收到拒绝后会转到其他实例或返回 502。
    serverSocket.close();

    // 2. 等在途请求排空，必须带超时（见下方 TimeoutStopSec 说明）
    workers.shutdown();
    if (!workers.awaitTermination(20, TimeUnit.SECONDS)) {
        workers.shutdownNow();        // 中断仍在跑的；跑不掉的只能放弃
    }

    // 3. 逐站回调，让站关自己的资源（连接池、定时任务）
    for (Site s : sites) {
        try { s.index().stop(); } catch (Exception e) { /* 记录，但不能中断其他站 */ }
    }

    // 4. 刷日志、关文件 handle
    logAppenders.forEach(LogAppender::close);
}
```

第 3 步就是 MyBatis 之类资源的归宿：**站在自己的 `stop()` 里关掉连接池**，
因为没有任何自动机制会替它做（见上面"为什么没有析构函数"）。

**超时怎么定**：systemd 的 `TimeoutStopSec` 默认 **90 秒**，超时后直接 SIGKILL。
所以第 2 步的排空超时**必须明显短于它**，给第 3、4 步留出余量。建议排空 15–30 秒，
整个钩子控制在 60 秒内结束。

#### 一条不能忘的前提

`kill -9`、JVM 崩溃、断电，**钩子都收不到**——这是操作系统层面的设计（内核必须保证进程总能被杀掉），
任何语言任何框架都一样。

推论：**优雅停机是用来减少损失的，不能用来保证正确性。** 任何"必须落地"的状态
（订单、支付、计数）都得自己是崩溃安全的——写数据库、写文件并 fsync——
而不是指望停机时再刷一次。钩子只负责让**正常发版**不掉请求。

**关联**：请求级的 500 兜底和进程级的停机是两个不同的层，但要回答同一个问题——
"已经开始的工作，要么完成，要么明确失败，不能不明不白地消失"。设计时建议一起考虑。

---

### 2. 站级生命周期回调（取代原先的"框架自带连接池"）

**修订说明**：原先列的是"框架必须提供数据库连接池"。既然站可以自带 MyBatis
（它自带 `PooledDataSource`）或 HikariCP，**框架不该提供池**——那违反零依赖，也重复造轮子。

框架真正必须提供的是**站的生命周期钩子**，否则站没有地方释放资源：

```java
public interface Index {
    void handle(Context ctx);          // 每请求
    default void start(Site site) {}   // 站加载时：建连接池、预热缓存
    default void stop() {}             // 站卸载/进程停机时：关池、停定时任务
}
```

没有 `stop()`，连接池永远关不掉，优雅停机就是半截的。

#### MyBatis 的两个坑（多站架构特有，必须提前知道）

**坑一：context classloader 不设，MyBatis 直接坏。**
MyBatis 通过 `Thread.currentThread().getContextClassLoader()` 加载 mapper 和实体类。
派发到站代码之前必须：

```java
Thread.currentThread().setContextClassLoader(site.classLoader());
// ... 调用 site 的 Index
// finally 恢复原值
```

这条之前是抽象的"classloader 注意事项"，遇上 MyBatis 就变成硬性要求。

**坑二：JDBC 驱动会钉住 classloader，造成内存泄漏。**
`DriverManager` 是 **JVM 全局注册表**。每个站 jar 各带一份 MySQL 驱动，就各自往全局表里注册，
驱动实例持有站的 classloader 引用 → **站卸载后 classloader 无法回收**。这是 Tomcat 填了十几年的经典坑。

**对策**：JDBC 驱动放**共享层**（`deploy/lib/`，由父 classloader 加载），不要放进站 jar。
站 jar 只带 MyBatis 本身。

---

### 3. JSON 序列化

**JDK 没有 JSON API。** 常见误解澄清：

- JSR 353 / `javax.json` 是 Jakarta EE 的，不在 JDK 里
- JEP 198（轻量 JSON API）多年前已撤回
- Nashorn（能间接解析 JSON）在 JDK 15 已移除

所以 JDK 21 里确实一个都没有。

**为什么必须做**：现在 `ctx.json(String)` 要求调用方自己拼字符串。**手拼 JSON 一定会出转义漏洞**——
用户昵称里一个 `"` 就能破坏结构，一个 `</script>` 就能构成 XSS。这不是"可能出问题"，是必然。

**成本极低**：libstrapper 的 `Json.java`（269 行）已经把**解析**写好了，`JsonValue` 的类型体系也在，
只缺**序列化**方向。补一个 writer 加正确的转义即可。

> 注意 libstrapper 是站级依赖，框架不能直接用它——需要把 Json 那部分复制进框架，或者把它抽成
> 一个两边都能用的最小模块。这个取舍要明确定下来，别让框架偷偷长出一个依赖。

---

### 4. 密码哈希

**⚠ 这里有个必须澄清的方向问题：密码哈希要的是「慢」，SHA 家族的设计目标是「快」。**

不是"SHA-1 还是 SHA-256"的选择，是**类别错误**——通用哈希函数不能用作密码哈希：

| 方案 | GPU 每秒可算 | 8 位密码爆破 |
|---|---|---|
| SHA-1 | ~10^10 | 几分钟 |
| SHA-256 | ~10^9 | 几十分钟 |
| PBKDF2-HMAC-SHA256（60 万次迭代） | ~10^3~10^4 | 数百年 |

SHA-1 另有独立问题：抗碰撞性已于 2017 年被 SHAttered 攻破。

**零依赖下的正确答案**：JDK 内置的 **PBKDF2WithHmacSHA256**

```java
SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
```

- 每个密码独立随机盐（`SecureRandom`，16 字节）
- 迭代次数按 OWASP 近期建议设到 **60 万** 量级，且**存进哈希串里**，方便日后调高
- 存储格式建议 `pbkdf2_sha256$<iterations>$<salt_b64>$<hash_b64>`，自描述、可平滑升级
- 校验用**恒定时间比较**（`MessageDigest.isEqual`），不要用 `equals`

Argon2id / bcrypt / scrypt 更好（内存硬化，抗 GPU），但都要引依赖。PBKDF2 是零依赖前提下的正解。

**框架必须提供这个**，否则十个团队里有八个会写 `md5(password)`。这类东西框架提供了才叫负责任。

---

## 二、决定"开箱即用"成色（三条）

### 5. ~~模板引擎~~ —— 已决定不做

PHP 版的 `templates/` 目录是早年用 Smarty 留下的，之后全部改走 API。**框架定位为 API 优先**。

连带影响：

- HTML 自动转义从"必须"降级为"可选工具函数"
- 但**开发模式下的静态文件处理器仍建议保留**——本地没有 nginx，否则跑不起完整前端联调

### 6. 统一错误处理

**现状**：异常一律 500 + 纯文本，站无法自定义。

需要区分两类（详见团队关于状态码的结论）：

- **业务失败**（余额不足、找不到记录）→ 2xx/4xx + body 里的业务码，**绝不能是 5xx**
- **程序崩溃**（NPE、连不上库）→ 5xx，且这才是告警该响应的

**归属**：这属于站的流程，应该由 `Index` 定义错误响应的形状，框架只提供"没人处理时"的最后兜底。

**硬约束建议**：框架自身产生的 5xx 只在框架真的出错时出现。业务说"这个不能恢复"永远不该走到 5xx——
否则 5xx 被业务噪声淹没，告警就失效了。

### 7. 不起服务就能测 controller

**现状**：测一个 action 要先起 HTTP 服务、发真请求。

需要一个测试辅助：构造 `Context` → 直接派发 → 拿回响应，全程不碰 socket。
这是 Javalin 这类框架的实际卖点之一，成本不高，但决定了使用者能不能写单元测试。

---

## 三、便宜的安全基线

### 8. Cookie 读取 API

现在 `Context` 只有**写**（`cookie(name, value, attrs)`），读 cookie 的逻辑埋在 `FrontController`
的私有方法里，业务代码拿不到。补 `ctx.cookie(name)`，几行的事。

### 9. 安全响应头默认值

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY          （按需放宽）
```

一行默认值挡掉两类常见问题。

### 10. HTML 转义工具函数

因为不做模板，这条降级为可选。但只要有任何拼 HTML 的地方就该用，提供一个总比让人现写好。

### 11.（补）CSRF

若走 JWT + Authorization 头，天然免疫 CSRF。但框架里**同时存在 session + cookie**，
所以 cookie 流仍然存在。现有的 `SameSite=Lax` 已经挡掉大部分，若要更严需要 CSRF token——
建议明确写清"cookie 流的 CSRF 防护责任在站（CommonController）"，别留成没人认领的地带。

---

## 四、一个整合观察：密码学原语一次服务四处

**JWT、OSS 预签名、CSRF token、密码哈希——用的是同一组 JDK 内置原语**：

| 原语 | JDK 类 | 服务于 |
|---|---|---|
| HMAC-SHA256 | `javax.crypto.Mac` | JWT 签名、OSS 预签名、CSRF token |
| 安全随机 | `java.security.SecureRandom` | session id、盐、CSRF token |
| PBKDF2 | `javax.crypto.SecretKeyFactory` | 密码哈希 |
| 恒定时间比较 | `MessageDigest.isEqual` | 全部签名/哈希校验 |
| Base64URL | `java.util.Base64` | JWT 编解码 |

**别把 JWT 当独立模块做**，做一个小的 crypto 门面，一次投入服务四个场景。

**JWT 只做 HS256。** RS256 需要密钥对管理、公钥分发、轮换策略，是另一个量级的复杂度，
极简框架不该碰。另外三条 JWT 实现的纪律：

1. **必须校验 `alg` 头**，且只接受白名单内的算法——历史上最经典的 JWT 漏洞就是接受 `alg: none`
2. **必须校验 `exp`**，并留一点时钟偏移容忍（几十秒）
3. **签名校验用恒定时间比较**，不要用 `String.equals`

---

## 五、已经白拿的，写进文档就行

| 需求 | 现成方案 | 备注 |
|---|---|---|
| HTTP 客户端 | JDK 11+ `java.net.http.HttpClient` | 调微信 API、OSS、支付都够用，一行不用写 |
| TLS / HTTP2 / 压缩 / 静态文件 / 限流 | nginx | 已定为部署前提 |
| 文件上传 | OSS 预签名直传 | 流量不过服务器，框架不需要 multipart |
| 性能剖析 | JFR（JDK 内置） | 配合"虚拟线程按站命名"，可直接定位哪个站吃 CPU |
| 定时任务 | `ScheduledExecutorService` | **必须绑定站生命周期**（第 2 条的 `stop()`），否则 classloader 泄漏 |

---

## 六、明确「不做」的清单

极简框架最大的风险不是缺功能，是**慢慢长成 Spring**。明确拒绝比明确支持更重要：

- **不做 ORM** —— 站自己选（MyBatis 等）。框架的 `Controller._db` 占位字段应删除
- **不做依赖注入容器 / AOP** —— 构造函数注入够用，这两个是复杂度黑洞
- **不做插件 / 扩展点体系** —— classloader 已经是最好的扩展点
- **不做自己的日志抽象层** —— 就是那个文件追加器，不要造 SLF4J 的替代品
- **不做响应式 / 异步 API** —— 虚拟线程的全部意义就是让你能写阻塞代码
- **不做 JWT 的 RS256 / 密钥轮换** —— 见第四节
- **不做服务发现 / 配置中心 / 熔断** —— 这些是微服务框架的活，不是 Web 框架的

**这六条守住，框架能长期停在一两千行的量级；破一条，三年后就是另一个东西了。**

---

## 建议动手顺序

```
1. 优雅停机 + 站生命周期回调（Index.start/stop）   ← 生产门槛，且是 2 的前提
2. JSON 序列化                                     ← 成本最低、堵掉一整类事故
3. crypto 门面（HMAC / PBKDF2 / SecureRandom）     ← 一次做完，JWT 顺带解决
4. JWT（HS256）                                    ← 建立在 3 之上
5. 统一错误处理                                     ← 与请求级 500 兜底一起设计
6. 测试支持                                         ← 让使用者能写单测
7. Cookie 读取 / 安全头 / 转义工具                  ← 零碎但便宜
```

第 3 条把 JWT、OSS 预签名、CSRF token、密码哈希四件事一次解决，性价比最高。
