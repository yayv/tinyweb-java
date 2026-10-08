# Demo3 数据模型设计

无头 CMS 的核心数据模型。

## 实体关系图

```
User (1) -------- (*) Article
         author       
         
Category (1) -------- (*) Article
                      

Article (*) -------- (*) Tag
            (many-to-many)
            

Article (1) -------- (*) Comment
            


Comment (M) -------- (1) User
         author
```

## 核心实体

### 1. Article（文章）

文章是 CMS 的核心内容单元。

**属性**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 自增主键 |
| title | String | NOT NULL, UNIQUE | 文章标题，1-200 字 |
| slug | String | UNIQUE | URL 友好的标识符 |
| summary | String | NOT NULL | 摘要，1-500 字 |
| content | Text | NOT NULL | 完整正文 |
| featured_image | String | | 封面图片 URL |
| category_id | Long | FK | 所属分类 |
| status | Enum | NOT NULL | 状态：DRAFT/PUBLISHED/ARCHIVED |
| author_id | Long | FK | 作者 ID |
| views | Int | DEFAULT 0 | 浏览次数 |
| created_at | DateTime | NOT NULL | 创建时间 |
| updated_at | DateTime | NOT NULL | 更新时间 |
| published_at | DateTime | | 发布时间 |

**Java Model**

```java
public class Article {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String featuredImage;
    
    private Long categoryId;
    private Long authorId;
    
    private ArticleStatus status;  // DRAFT, PUBLISHED, ARCHIVED
    private Integer views;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    
    // 关系
    private List<String> tags;
    private List<Comment> comments;
    
    // 业务方法
    public void publish() { ... }
    public void archive() { ... }
    public void incrementViews() { ... }
}
```

### 2. Category（分类）

用于组织文章。支持层级结构。

**属性**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 主键 |
| name | String | NOT NULL, UNIQUE | 分类名称 |
| slug | String | UNIQUE | URL 友好的标识符 |
| description | String | | 分类描述 |
| parent_id | Long | FK | 父分类 ID（支持层级） |
| sort_order | Int | | 排序序号 |
| created_at | DateTime | NOT NULL | 创建时间 |

**Java Model**

```java
public class Category {
    private Long id;
    private String name;
    private String slug;
    private String description;
    
    private Long parentId;  // 父分类，支持层级
    private Integer sortOrder;
    
    private LocalDateTime createdAt;
    
    // 统计信息
    private Integer articleCount;  // 缓存字段
}
```

### 3. Tag（标签）

文章的灵活标签系统。

**属性**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 主键 |
| name | String | NOT NULL, UNIQUE | 标签名称 |
| slug | String | UNIQUE | URL 友好的标识符 |
| color | String | | 标签颜色（十六进制） |
| created_at | DateTime | NOT NULL | 创建时间 |

**Java Model**

```java
public class Tag {
    private Long id;
    private String name;
    private String slug;
    private String color;
    private LocalDateTime createdAt;
    
    // 统计信息
    private Integer articleCount;  // 使用次数
}
```

### 4. User（用户）

系统用户，支持多种角色。

**属性**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 主键 |
| username | String | NOT NULL, UNIQUE | 用户名 |
| email | String | NOT NULL, UNIQUE | 邮箱 |
| password | String | NOT NULL | 密码哈希 |
| role | Enum | NOT NULL | 角色：ADMIN/EDITOR/VIEWER |
| is_active | Boolean | DEFAULT true | 是否激活 |
| last_login | DateTime | | 最后登录时间 |
| created_at | DateTime | NOT NULL | 创建时间 |

**Java Model**

```java
public class User {
    private Long id;
    private String username;
    private String email;
    private String password;  // 哈希后
    
    private UserRole role;  // ADMIN, EDITOR, VIEWER
    private Boolean isActive;
    
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
}

enum UserRole {
    ADMIN,      // 管理员：全部权限
    EDITOR,     // 编辑：创建/修改/发布文章
    VIEWER      // 查看者：只读
}
```

### 5. Comment（评论）

文章下的评论。

**属性**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 主键 |
| article_id | Long | FK | 所属文章 |
| author_id | Long | FK | 评论者 |
| content | String | NOT NULL | 评论内容 |
| status | Enum | DEFAULT PENDING | 状态：PENDING/APPROVED/REJECTED |
| created_at | DateTime | NOT NULL | 创建时间 |

## 数据库架构

### SQL Schema 示例（SQLite 开发环境）

```sql
-- 用户表
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    is_active BOOLEAN DEFAULT 1,
    last_login DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 分类表
CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) UNIQUE,
    description TEXT,
    parent_id INTEGER,
    sort_order INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id)
);

-- 标签表
CREATE TABLE tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    slug VARCHAR(50) UNIQUE,
    color VARCHAR(10),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 文章表
CREATE TABLE articles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(200) NOT NULL UNIQUE,
    slug VARCHAR(200) UNIQUE,
    summary VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    featured_image VARCHAR(255),
    category_id INTEGER,
    author_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    views INTEGER DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (author_id) REFERENCES users(id)
);

-- 文章-标签关联表
CREATE TABLE article_tags (
    article_id INTEGER NOT NULL,
    tag_id INTEGER NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- 评论表
CREATE TABLE comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_id INTEGER NOT NULL,
    author_id INTEGER NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id)
);

-- 索引
CREATE INDEX idx_articles_category ON articles(category_id);
CREATE INDEX idx_articles_status ON articles(status);
CREATE INDEX idx_articles_published_at ON articles(published_at DESC);
CREATE INDEX idx_article_tags_tag_id ON article_tags(tag_id);
```

## 业务规则

### 文章状态转换

```
DRAFT ──publish──> PUBLISHED ──archive──> ARCHIVED
  ^                                          │
  └──────────────────────────────────────────┘
              restore
```

- **DRAFT**：草稿，只有作者可见
- **PUBLISHED**：已发布，所有人可见，评论可用
- **ARCHIVED**：已归档，不再显示在列表，但可查看

### 权限模型

| 操作 | ADMIN | EDITOR | VIEWER |
|------|-------|--------|--------|
| 创建文章 | ✅ | ✅ | ❌ |
| 修改文章 | ✅ | 自己的 | ❌ |
| 发布文章 | ✅ | ✅ | ❌ |
| 删除文章 | ✅ | 自己的 | ❌ |
| 管理分类 | ✅ | ❌ | ❌ |
| 审核评论 | ✅ | ❌ | ❌ |
| 查看文章 | ✅ | ✅ | ✅ |

### 缓存策略

- **热数据**：发布时间最近的 20 篇文章
- **分类缓存**：整个分类树（变动不频繁）
- **标签缓存**：按热度排序的 100 个标签
- **缓存失效**：创建/修改文章时清理相关缓存

## 未来扩展

- [ ] 修订历史（Version Control）
- [ ] 关键词和 SEO 优化
- [ ] 文章推荐算法
- [ ] 多语言支持
- [ ] 媒体库管理
- [ ] 工作流（编辑→审核→发布）
