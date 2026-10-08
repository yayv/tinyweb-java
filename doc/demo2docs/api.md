# Demo2 CMS API

## 端点

### POST /article2/create

创建文章。

**请求**
```
Content-Type: application/x-www-form-urlencoded

title=...&content=...
```

**响应** (201)
```json
{
  "id": 1,
  "title": "Test",
  "content": "Content",
  "createdAt": 1790398880960,
  "updatedAt": 1790398880960
}
```

---

### GET /article2/list

列出所有文章，按 id 倒序。

**请求**
```
GET /article2/list
```

**响应** (200)
```json
[
  {
    "id": 2,
    "title": "Second",
    "content": "Content",
    "createdAt": 1790398880960,
    "updatedAt": 1790398880960
  },
  {
    "id": 1,
    "title": "First",
    "content": "Content",
    "createdAt": 1790398880960,
    "updatedAt": 1790398880960
  }
]
```

---

### GET /article2/get/{id}

获取单篇文章。

**请求**
```
GET /article2/get/1
```

**响应** (200)
```json
{
  "id": 1,
  "title": "Test",
  "content": "Content",
  "createdAt": 1790398880960,
  "updatedAt": 1790398880960
}
```

**错误** (404)
```json
{
  "ok": false,
  "message": "article not found"
}
```

---

### POST /article2/update/{id}

更新文章。只更新提供的字段。

**请求**
```
Content-Type: application/x-www-form-urlencoded

title=Updated Title
```

或

```
content=Updated Content
```

或

```
title=Updated Title&content=Updated Content
```

**响应** (200)
```json
{
  "id": 1,
  "title": "Updated Title",
  "content": "Updated Content",
  "createdAt": 1790398880960,
  "updatedAt": 1790398881008
}
```

**错误** (404)
```json
{
  "ok": false,
  "message": "article not found"
}
```

---

### POST /article2/delete/{id}

删除文章。

**请求**
```
POST /article2/delete/1
```

**响应** (200)
```json
{
  "ok": true,
  "message": "deleted"
}
```

**错误** (404)
```json
{
  "ok": false,
  "message": "article not found"
}
```

---

## 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 文章唯一标识，自动生成 |
| title | string | 文章标题 |
| content | string | 文章内容 |
| createdAt | long | 创建时间戳（毫秒） |
| updatedAt | long | 最后修改时间戳（毫秒） |

---

## 启动

```bash
cd tinyweb-java/demo2src
java -Dtinyweb.home=. -cp out:lib/mybatis.jar:lib/h2.jar:../dist/tinyweb.jar demo.Main 8081
```

---

## 快速测试

```bash
# 创建
curl -X POST http://localhost:8081/article2/create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "title=Test&content=Content"

# 列表
curl http://localhost:8081/article2/list

# 获取
curl http://localhost:8081/article2/get/1

# 更新
curl -X POST http://localhost:8081/article2/update/1 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "title=Updated"

# 删除
curl -X POST http://localhost:8081/article2/delete/1
```
