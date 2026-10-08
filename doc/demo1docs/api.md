# Demo1 API 文档

## 概述

Demo1 是一个极简的 Web 服务示例，展示 tinyWEB-java 框架的核心功能：
- JWT 令牌认证
- 模型验证
- RESTful API 设计

## 认证

所有接口都是无状态的，使用 JWT（JSON Web Token）进行认证。成功登录后，客户端获得一个 JWT 令牌，在后续请求中通过 `Authorization` 头传递：

```
Authorization: Bearer <token>
```

（本 demo 目前暂未验证令牌，仅展示生成和返回的流程）

---

## 接口列表

### 1. 用户登录

**端点** `POST /user/login`

登录接口，验证用户名和密码，返回 JWT 令牌。

#### 请求

**请求头：**
```
Content-Type: application/x-www-form-urlencoded
```

**请求体参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

#### 响应

**成功 (200 OK)：**

```json
{
  "ok": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc5MDI3ODQzNiwiZXhwIjoxNzkwMzY0ODM2fQ.sOxCZWeeFQyjhYTLByD2rzNoK6D13klj3amFXbTtN34"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| ok | boolean | 操作是否成功 |
| token | string | JWT 令牌，有效期 24 小时 |

**失败 (401 Unauthorized)：**

```json
{
  "ok": false,
  "message": "用户名或密码错误"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| ok | boolean | 操作是否成功 |
| message | string | 错误信息 |

#### 示例

```bash
# 正确的凭证
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=test123"

# 响应
{
  "ok": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

# 错误的凭证
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=wrong"

# 响应
{
  "ok": false,
  "message": "用户名或密码错误"
}
```

#### 备注

- 硬编码凭证：`username=admin`, `password=test123`（demo 用途，实际应从数据库查询）
- JWT 令牌采用 **HS256** 算法签名，24 小时后过期
- 令牌载荷包含：`sub`（subject，用户名）、`iat`（issued at）、`exp`（expiration）

---

### 2. 用户登出

**端点** `POST /user/logout`

登出接口，清除客户端会话。

#### 请求

无请求体参数。

#### 响应

**成功 (200 OK)：**

```json
{
  "ok": true,
  "message": "logged out"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| ok | boolean | 操作是否成功 |
| message | string | 操作信息 |

#### 示例

```bash
curl -X POST http://localhost:8080/user/logout

# 响应
{
  "ok": true,
  "message": "logged out"
}
```

#### 备注

- JWT 系统中，登出通常在客户端处理（删除本地存储的令牌）
- 本接口作为示例，仅返回成功响应，实际可扩展为黑名单、令牌作废等机制

---

## 数据模型

### 用户模型 (MUser)

用户验证模型，包含用户名和密码验证逻辑。

**字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| username | string | 用户名 |
| password | string | 密码 |

**验证规则：**

| 字段 | 验证 | 错误信息 |
|------|------|------|
| username | 非空 | 用户名不能为空 |
| password | 非空 | 密码不能为空 |
| 凭证匹配 | username=admin 且 password=test123 | 用户名或密码错误 |

---

## 安全考虑

### 当前 Demo 中的简化

1. **硬编码凭证** — 仅用于示例，实际应查询数据库并使用密码哈希（如 PBKDF2）
2. **无令牌验证** — login 和 logout 都不验证收到的令牌
3. **无 HTTPS** — 示例环境不走 TLS，实际需在反向代理（nginx）层启用

### 建议的改进方向

1. 集成数据库，存储加盐哈希的密码
2. 在受保护接口中验证 JWT 令牌的签名和有效期
3. 部署在 nginx 反向代理后，启用 HTTPS
4. 添加速率限制，防止暴力破解
5. 实现令牌黑名单或刷新令牌机制

---

## 错误处理

所有 API 错误都以 JSON 格式返回，包含 `ok` 和 `message` 字段：

```json
{
  "ok": false,
  "message": "错误描述"
}
```

**HTTP 状态码：**

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 401 | 认证失败（用户名/密码错误） |
| 500 | 服务器错误 |

---

## 启动和测试

### 启动服务

```bash
cd tinyweb-java/demo1src
java -Dtinyweb.home=. -cp out:../dist/tinyweb.jar demo.Main 8080
```

### 快速测试

```bash
# 测试登录（成功）
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=test123" | jq .

# 测试登录（失败）
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=wrong" | jq .

# 测试登出
curl -X POST http://localhost:8080/user/logout | jq .
```

---

## 代码结构

```
demo1src/src/demo/
├── Main.java          # 应用入口
├── User.java          # 用户控制器（路由：/user）
├── MUser.java         # 用户模型
├── TokenUtils.java    # JWT 工具类
├── Home.java          # 示例控制器
├── Greeter.java       # 示例模型
└── DefaultController  # 默认控制器（框架提供）
```

---

## 后续计划

- **Demo 2** — 集成第三方库（数据库驱动、模板引擎）
- **Demo 3** — 多站点部署
- **Index.start/stop** — 实现站级生命周期回调，支持资源初始化和清理
