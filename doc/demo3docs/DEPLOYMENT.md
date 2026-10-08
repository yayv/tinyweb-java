# Demo3 部署指南

Demo3 无头 CMS 的部署和运维指南。

## 开发环境搭建

### 前置要求

- JDK 21+
- tinyWEB 框架（已内置）
- SQLite（开发用，可选其他数据库）

### 构建与运行

#### 单独启动 Demo3

```bash
cd tinyweb-java/demo3src
bash build.sh
bash run.sh 8083
```

#### 与多个站点一起运行

```bash
cd tinyweb-java
java -jar dist/tinyweb.jar
# 根据提示启动多站点模式
```

### 配置文件

**demo3src/configs/cfg.default.conf**

```properties
# 应用配置
app.controllerPackage=demo3.Controller
app.name=tinyWEB Demo3 CMS

# 数据库配置
db.driver=sqlite
db.url=jdbc:sqlite:demo3.db
db.maxPoolSize=10

# 缓存配置
cache.enabled=true
cache.ttl=3600

# API 配置
api.pageSize.default=20
api.pageSize.max=100

# 认证配置
auth.tokenExpiry=86400
auth.tokenSecret=your-secret-key-change-in-production
```

## 多站点部署

当 demo1、demo2、demo3 一起运行时：

### 目录结构

```
deployment/
├── sites/
│   ├── site-demo1.jar      # Demo1 站点 jar
│   ├── site-demo2.jar      # Demo2 站点 jar
│   └── site-demo3.jar      # Demo3 站点 jar
├── demo1/
│   ├── configs/
│   │   ├── cfg.demo1.conf
│   │   └── cmap.default.conf
│   └── logs/
├── demo2/
│   ├── configs/
│   │   ├── cfg.demo2.conf
│   │   └── cmap.default.conf
│   └── logs/
├── demo3/
│   ├── configs/
│   │   ├── cfg.demo3.conf
│   │   └── cmap.default.conf
│   └── logs/
│   └── data/               # 数据库和媒体文件
└── default/                # 默认站点
    ├── configs/
    └── logs/
```

### 配置示例

**demo3/configs/cfg.demo3.conf**

```properties
app.controllerPackage=demo3.Controller
app.name=Demo3 CMS

# 数据库本地化
db.url=jdbc:sqlite:demo3/data/cms.db

# 日志
log.level=INFO
log.dir=demo3/logs

# 站点特定配置
site.title=tinyWEB 无头 CMS
site.port=8083
site.baseUrl=http://cms.example.com
```

### 启动多站点

```bash
cd deployment
java -jar ../tinyweb-java/dist/tinyweb.jar 8000
```

系统会自动：
1. 加载 `sites/` 下的所有 jar
2. 为每个站点读取对应目录下的 `cfg.*.conf`
3. 按 Host 头路由请求

## 生产环境部署

### 使用 Docker

**Dockerfile**

```dockerfile
FROM openjdk:21-slim

WORKDIR /app

# 复制 jar 和配置
COPY tinyweb.jar .
COPY demo3/configs ./configs
COPY demo3/data ./data

# 启动命令
CMD ["java", "-jar", "tinyweb.jar"]

# 暴露端口
EXPOSE 8083
```

### 使用 Nginx 反向代理

```nginx
upstream demo3_cms {
    server localhost:8083 weight=1;
    server localhost:8084 weight=1;  # 多实例负载均衡
}

server {
    listen 80;
    server_name cms.example.com;

    location / {
        proxy_pass http://demo3_cms;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 为 API 请求增加超时
        proxy_read_timeout 30s;
        proxy_connect_timeout 10s;
    }

    # 静态文件缓存
    location ~* \.(jpg|jpeg|png|gif|css|js)$ {
        proxy_pass http://demo3_cms;
        proxy_cache_valid 200 30d;
        expires 30d;
    }
}
```

### 性能优化

#### 1. 连接池配置

```properties
# 数据库连接池
db.maxPoolSize=20
db.minPoolSize=5
db.connectionTimeout=30000
db.idleTimeout=600000

# 线程池
server.threadPool.core=10
server.threadPool.max=50
server.threadPool.queue=100
```

#### 2. 缓存策略

```properties
# Redis 缓存（可选）
cache.type=redis
cache.redis.host=localhost
cache.redis.port=6379
cache.redis.db=1

# 缓存 TTL（单位：秒）
cache.article.ttl=3600      # 文章 1 小时
cache.category.ttl=86400    # 分类 1 天
cache.tag.ttl=86400         # 标签 1 天
cache.user.ttl=300          # 用户 5 分钟
```

#### 3. 数据库优化

```sql
-- 关键查询索引
CREATE INDEX idx_articles_status_published ON articles(status, published_at DESC);
CREATE INDEX idx_article_tags_article ON article_tags(article_id);
CREATE INDEX idx_comments_article ON comments(article_id, status);

-- 定期分析表
ANALYZE;
```

### 监控与日志

#### 日志配置

```properties
# 日志级别
log.level.app=INFO
log.level.db=DEBUG
log.level.api=INFO

# 日志输出
log.appender.file=true
log.file.path=demo3/logs/cms.log
log.file.maxSize=100MB
log.file.maxBackup=10

# 访问日志
log.access.enabled=true
log.access.file=demo3/logs/access.log
```

#### 关键指标

- 📊 **请求延迟**：API 平均响应时间
- 📊 **吞吐量**：每秒处理请求数
- 📊 **错误率**：5xx 错误比例
- 📊 **缓存命中率**：缓存使用效率
- 📊 **数据库连接数**：连接池利用率

### 备份策略

```bash
# 定时备份脚本（cron）
0 2 * * * /app/backup.sh

# backup.sh
#!/bin/bash
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
sqlite3 demo3/data/cms.db ".backup /backup/cms_$TIMESTAMP.db"
gzip /backup/cms_$TIMESTAMP.db
# 删除 7 天前的备份
find /backup -name "cms_*.db.gz" -mtime +7 -delete
```

## 常见问题排查

### 数据库连接失败

```
Error: Database connection refused
```

**解决方案**：
1. 检查数据库服务是否运行
2. 验证连接字符串（URL、端口、用户名）
3. 检查防火墙规则

### 内存溢出

```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：
```bash
java -Xmx2G -Xms1G -jar tinyweb.jar  # 设置堆内存
```

### 高并发导致响应缓慢

**解决方案**：
1. 启用缓存
2. 增加数据库连接池
3. 使用 Nginx 负载均衡
4. 考虑分库分表

## 升级指南

### 小版本升级（Bug 修复）

```bash
# 1. 备份数据
cp demo3/data/cms.db demo3/data/cms.db.backup

# 2. 停止服务
kill $(pgrep -f "tinyweb.jar")

# 3. 更新 jar
cp new-tinyweb.jar tinyweb.jar

# 4. 重启
java -jar tinyweb.jar
```

### 大版本升级（数据库变更）

1. 执行数据库迁移脚本
2. 通常需要停机
3. 执行数据验证
4. 逐步灰度发布

## 生产检查清单

- [ ] 数据库备份已配置
- [ ] 日志轮转已启用
- [ ] 监控告警已部署
- [ ] SSL 证书已配置
- [ ] 认证 Token Secret 已更改
- [ ] 数据库连接池已调优
- [ ] 缓存已启用
- [ ] Nginx 反向代理已配置
- [ ] 负载均衡已测试
- [ ] 灾难恢复计划已制定

## 技术支持

遇到问题？
- 查看日志：`demo3/logs/cms.log`
- 检查配置：`demo3/configs/cfg.demo3.conf`
- 查阅 API 文档：`docs/demo3docs/API.md`
