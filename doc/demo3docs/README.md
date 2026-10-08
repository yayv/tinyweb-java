# Demo3：无头 CMS 服务

Demo3 展示如何使用 tinyWEB 框架构建一个**无头 CMS（Headless CMS）服务**。

## 概述

无头 CMS 是一个内容管理系统，它：
- 🔄 **管理内容**（文章、页面、媒体等）
- 📡 **通过 API 提供**内容，而不是直接生成 HTML
- 🎯 **解耦内容与展现**，支持多渠道发布（Web、移动应用、第三方平台）

## 核心功能

### 1. 内容模型

- [ ] 文章（Article）
  - 标题、摘要、正文
  - 分类、标签
  - 发布状态、发布日期
  - 作者、修改历史

- [ ] 分类（Category）
  - 名称、描述
  - 层级关系

- [ ] 标签（Tag）
  - 名称、使用次数

### 2. 核心 API

#### 内容管理接口
- `GET /api/articles` — 列表
- `GET /api/articles/{id}` — 详情
- `POST /api/articles` — 创建
- `PUT /api/articles/{id}` — 修改
- `DELETE /api/articles/{id}` — 删除

#### 分类接口
- `GET /api/categories` — 列表
- `POST /api/categories` — 创建

#### 标签接口
- `GET /api/tags` — 列表

### 3. 存储层

- [ ] 数据持久化
  - 数据库选型
  - 表结构设计

- [ ] 缓存策略
  - 热数据缓存
  - 缓存失效机制

### 4. 权限与认证

- [ ] 用户认证
  - 登录/注册
  - Token 管理

- [ ] 权限控制
  - 发布权限
  - 管理权限

## 项目结构

```
demo3src/
├── src/demo3/
│   ├── Controller/          # API 控制器
│   │   ├── ArticleController.java
│   │   ├── CategoryController.java
│   │   └── TagController.java
│   ├── Model/               # 业务模型
│   │   ├── Article.java
│   │   ├── Category.java
│   │   └── Tag.java
│   ├── Service/             # 业务逻辑层
│   │   └── ArticleService.java
│   └── Main.java            # 启动入口
├── configs/
│   └── cfg.default.conf     # 配置文件
└── README.md                # 本文档
```

## 启动方式

### 单独启动 demo3

```bash
cd tinyweb-java/demo3src
bash build.sh
bash run.sh 8083
```

### 与 demo1、demo2 一起运行（多站点）

```bash
cd tinyweb-java
java -jar dist/tinyweb.jar
# 会同时启动多个站点
```

## 测试示例

### 创建文章

```bash
curl -X POST http://localhost:8083/article/create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "title=第一篇文章&content=这是内容&category=技术"
```

### 获取文章列表

```bash
curl http://localhost:8083/article/list
```

### 获取文章详情

```bash
curl http://localhost:8083/article/view/1
```

## 多站点运行

当 demo1、demo2、demo3 同时运行时：
- demo1 监听某个 Host 配置
- demo2 监听另一个 Host 配置  
- demo3 运行为第三个站点

这样可以验证 tinyWEB 框架的多站点支持。

## 技术栈

- **框架**：tinyWEB-java
- **数据存储**：（待确定）
- **缓存**：（待确定）
- **认证**：Token-based

## 下一步

- [ ] 完成数据模型实现
- [ ] 实现核心 CRUD API
- [ ] 添加分页、过滤、排序
- [ ] 实现认证与权限控制
- [ ] 编写测试用例
- [ ] 性能优化与缓存策略

## 相关文档

- [API 文档](./API.md)
- [数据模型设计](./MODEL_DESIGN.md)
- [部署指南](./DEPLOYMENT.md)
