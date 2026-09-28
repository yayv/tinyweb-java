package demo.Model;

import top.x0a.tinyweb.Model;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServe extends Model {
    private String filePath;
    private byte[] content;
    private String contentType;

    public boolean loadFile(String resourcePath) {
        try {
            String jarPath = FileServe.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            if (jarPath.endsWith(".jar")) {
                return loadFromJar(resourcePath);
            } else {
                return loadFromFileSystem(resourcePath);
            }
        } catch (Exception e) {
            pushError("file", "无法加载文件: " + e.getMessage());
            return false;
        }
    }

    private boolean loadFromFileSystem(String resourcePath) throws IOException {
        Path path = Paths.get("resource").resolve(resourcePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            pushError("file", "文件不存在: " + resourcePath);
            return false;
        }

        this.filePath = resourcePath;
        this.content = Files.readAllBytes(path);
        this.contentType = guessMimeType(resourcePath);
        return true;
    }

    private boolean loadFromJar(String resourcePath) throws IOException {
        ClassLoader cl = FileServe.class.getClassLoader();
        java.io.InputStream is = cl.getResourceAsStream(resourcePath);
        if (is == null) {
            pushError("file", "资源不存在: " + resourcePath);
            return false;
        }

        this.filePath = resourcePath;
        this.content = is.readAllBytes();
        is.close();
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
