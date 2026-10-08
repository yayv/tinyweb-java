# 技术债清单

记录框架中已知的改进空间，便于后续迭代。

## FileServeModel 和 JarFileServeModel

### 问题 1：读取策略硬编码

**现状**：`JarFileServeModel.loadFile()` 中硬编码判断是否为 jar：
```java
if (!jarPath.endsWith(".jar")) {
    return super.loadFile(resourcePath);  // 降级到文件系统
}
```

**改进方案**：
- 将判断逻辑抽象为可配置的"策略"或"规则"
- 通过构造函数或单独的 `setStrategy()` 方法设置
- 示例：
  ```java
  model.setFileLoadStrategy(FileLoadStrategy.JAR_WITH_FALLBACK);
  ```

### 问题 2：缺少根路径限制

**现状**：没有设置绝对的根路径，可能被目录遍历攻击：
```java
// 用户可以请求：../../../etc/passwd
Path path = Paths.get("resource").resolve(resourcePath);
```

**改进方案**：
- 在构造函数中设置安全的根目录
- 对所有路径做规范化和检查，确保不超出根目录
- 示例：
  ```java
  public FileServeModel(Path rootDir) {
      this.rootDir = rootDir.toAbsolutePath();
  }
  
  private boolean isPathSafe(Path targetPath) {
      return targetPath.toAbsolutePath().startsWith(rootDir);
  }
  ```

### 问题 3：根目录硬编码

**现状**：根目录硬编码为 `"resource"`
```java
private static final String DEFAULT_ROOT = "resource";
```

**改进方案**：
- 允许在构造函数中指定根目录
- 支持绝对路径和相对路径
- 示例：
  ```java
  FileServeModel model = new FileServeModel(Paths.get("/app/resources"));
  ```

### 问题 4：继承关系不清晰

**现状**：`JarFileServeModel extends FileServeModel`，但两者有不同的读取源

**改进方案**：
- 抽象出公共接口或抽象基类
- 分别实现 JarFileServe 和 FileSystemServe 两个独立的类
- 或者用组合而非继承：`FileServeModel` 内部持有可插拔的 `FileSource` 接口

## 使用示例（改进后）

```java
// 从 jar 包读取
FileServeModel jarModel = new FileServeModel(
    Paths.get("static_pages"),
    FileSource.JAR
);

// 从文件系统读取
FileServeModel fsModel = new FileServeModel(
    Paths.get("/var/www/static"),
    FileSource.FILESYSTEM
);

// 优先 jar，降级到文件系统
FileServeModel hybrid = new FileServeModel(
    Paths.get("resources"),
    FileSource.JAR_WITH_FALLBACK
);
```

## 优先级

- 🔴 **高**：问题 2（安全性） — 防止目录遍历攻击
- 🟡 **中**：问题 1 和 3（架构） — 提高灵活性和可配置性
- 🟢 **低**：问题 4（代码质量） — 重构继承关系

## 相关代码位置

- `src/main/java/top/x0a/tinyweb/FileServeModel.java`
- `src/main/java/top/x0a/tinyweb/JarFileServeModel.java`
- `demo1src/src/demo/Controller/Static.java`
