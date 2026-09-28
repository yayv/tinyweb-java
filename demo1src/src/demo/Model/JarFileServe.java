package demo.Model;

import top.x0a.tinyweb.Model;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileServe extends Model {
    private byte[] content;
    private String contentType;
    private String filePath;

    /**
     * 从当前 jar 包中加载文件
     * @param resourcePath jar 内的资源路径，相对于 jar 根目录
     * @return 是否加载成功
     */
    public boolean loadFile(String resourcePath) {
        try {
            String jarPath = JarFileServe.class.getProtectionDomain().getCodeSource().getLocation().getPath();

            // 如果不是 jar 包（开发环境），降级到文件系统读取
            if (!jarPath.endsWith(".jar")) {
                return loadFromFileSystem(resourcePath);
            }

            return loadFromJar(jarPath, resourcePath);
        } catch (Exception e) {
            pushError("jar_file", "无法加载文件: " + e.getMessage());
            return false;
        }
    }

    private boolean loadFromJar(String jarPath, String resourcePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            JarEntry entry = jarFile.getJarEntry(resourcePath);
            if (entry == null) {
                pushError("jar_file", "jar 包中文件不存在: " + resourcePath);
                return false;
            }

            this.filePath = resourcePath;
            this.content = jarFile.getInputStream(entry).readAllBytes();
            this.contentType = guessMimeType(resourcePath);
            return true;
        }
    }

    private boolean loadFromFileSystem(String resourcePath) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get("resource").resolve(resourcePath);
        if (!java.nio.file.Files.exists(path) || !java.nio.file.Files.isRegularFile(path)) {
            pushError("jar_file", "文件不存在: " + resourcePath);
            return false;
        }

        this.filePath = resourcePath;
        this.content = java.nio.file.Files.readAllBytes(path);
        this.contentType = guessMimeType(resourcePath);
        return true;
    }

    private String guessMimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isLoaded() {
        return content != null && content.length > 0;
    }
}
