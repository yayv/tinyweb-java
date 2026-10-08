# Demo3 CMS API 文档

无头 CMS 服务的完整 API 规范。

## 基础信息

- **Base URL**：`http://localhost:8083`
- **Content-Type**：`application/json`（响应）
- **认证**：Token-based（`Authorization: Bearer <token>`）

## 响应格式

所有 API 响应遵循统一的格式：

```json
{
  "ok": true,
  "data": {...},
  "message": "成功",
  "timestamp": "2026-09-28T21:40:00Z"
}
```

错误响应：

```json
{
  "ok": false,
  "message": "错误信息",
  "code": "ERROR_CODE",
  "timestamp": "2026-09-28T21:40:00Z"
}
```

## 文章管理 API

### 获取文章列表

```
GET /article/list
```

**查询参数**

| 参数 | 类型 | 必须 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页数量，默认 10 |
| category | string | 否 | 分类筛选 |
| tag | string | 否 | 标签筛选 |
| status | string | 否 | 状态：draft/published |
| sort | string | 否 | 排序：date_desc/date_asc/views |

**示例请求**

```bash
curl "http://localhost:8083/article/list?page=1&pageSize=20&status=published"
```

**示例响应**

```json
{
  "ok": true,
  "data": {
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "id": 1,
        "title": "第一篇文章",
        "summary": "这是摘要...",
        "category": "技术",
        "tags": ["Java", "Web"],
        "author": "admin",
        "status": "published",
        "views": 1234,
        "createdAt": "2026-09-28T12:00:00Z",
        "updatedAt": "2026-09-28T12:00:00Z"
      }
    ]
  }
}
```

### 获取文章详情

```
GET /article/view/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| id | 文章 ID |

**示例请求**

```bash
curl http://localhost:8083/article/view/1
```

**示例响应**

```json
{
  "ok": true,
  "data": {
    "id": 1,
    "title": "第一篇文章",
    "summary": "摘要",
    "content": "完整内容...",
    "category": "技术",
    "tags": ["Java", "Web"],
    "author": "admin",
    "status": "published",
    "views": 1234,
    "createdAt": "2026-09-28T12:00:00Z",
    "updatedAt": "2026-09-28T12:00:00Z",
    "comments": []
  }
}
```

### 创建文章

```
POST /article/create
```

**请求体**（Form 或 JSON）

| 字段 | 类型 | 必须 | 说明 |
|------|------|------|------|
| title | string | ✓ | 文章标题，1-200 字 |
| summary | string | ✓ | 摘要，1-500 字 |
| content | string | ✓ | 正文内容 |
| category | string | ✓ | 分类 |
| tags | array | 否 | 标签数组 |
| status | string | 否 | 状态：draft/published，默认 draft |

**示例请求**

```bash
curl -X POST http://localhost:8083/article/create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d "title=新文章&summary=摘要&content=内容&category=技术&tags=java,web&status=published"
```

**示例响应**

```json
{
  "ok": true,
  "data": {
    "id": 101,
    "title": "新文章",
    "createdAt": "2026-09-28T21:40:00Z"
  },
  "message": "文章创建成功"
}
```

### 更新文章

```
PUT /article/update/{id}
```

**路径参数**

| 参数 | 说明 |
|------|------|
| id | 文章 ID |

**请求体**

同创建文章，但所有字段都是可选的（只更新提供的字段）

**示例请求**

```bash
curl -X PUT http://localhost:8083/article/update/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"title":"更新后的标题","status":"published"}'
```

### 删除文章

```
DELETE /article/delete/{id}
```

**示例请求**

```bash
curl -X DELETE http://localhost:8083/article/delete/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 分类管理 API

### 获取分类列表

```
GET /category/list
```

**示例响应**

```json
{
  "ok": true,
  "data": [
    {"id": 1, "name": "技术", "articleCount": 45},
    {"id": 2, "name": "生活", "articleCount": 23}
  ]
}
```

### 创建分类

```
POST /category/create
```

**请求体**

```json
{
  "name": "新分类",
  "description": "分类描述"
}
```

## 标签管理 API

### 获取标签列表

```
GET /tag/list
```

**查询参数**

| 参数 | 说明 |
|------|------|
| sort | 排序：name/count，默认 count |

## 认证 API

### 登录

```
POST /auth/login
```

**请求体**

```json
{
  "username": "admin",
  "password": "password"
}
```

**响应**

```json
{
  "ok": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| `ARTICLE_NOT_FOUND` | 文章不存在 |
| `CATEGORY_NOT_FOUND` | 分类不存在 |
| `UNAUTHORIZED` | 未授权 |
| `FORBIDDEN` | 无权限 |
| `VALIDATION_ERROR` | 验证错误 |
| `SERVER_ERROR` | 服务器错误 |

## 速率限制

- 每 IP 每分钟最多 60 个请求
- 认证用户每分钟最多 100 个请求

## 版本历史

- **v1.0**（当前）— 初始版本
