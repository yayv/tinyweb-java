package top.x0a.tinyweb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 文件服务模型：从文件系统读取文件
 * 用于在本地文件系统中提供文件访问
 *
 * TODO: 待改进的架构问题
 * 1. 根目录硬编码为 "resource"，应该通过构造函数或配置指定
 * 2. 缺少路径安全检查，可能被目录遍历攻击（../../../etc/passwd）
 * 3. 读取策略应该可配置，而非硬编码为文件系统
 */
public class FileServeModel extends Model {
    /** HTTP-date 必须是两位日期（IMF-fixdate），JDK 的 RFC_1123_DATE_TIME 写出时日期不补零，只拿来解析 */
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    protected byte[] content;
    protected String contentType;
    protected String filePath;
    protected String etag;
    protected long lastModified;   // 毫秒；0 表示未知
    protected boolean notModified;

    /**
     * 从文件系统加载文件
     * @param resourcePath 相对于项目目录的文件路径
     * @return 是否加载成功
     */
    public boolean loadFile(String resourcePath) {
        return loadFile(resourcePath, null, null);
    }

    /**
     * 带条件请求的加载：先取文件元信息算出 ETag / Last-Modified，与请求头比对；
     * 没变就不读内容，{@link #isNotModified()} 为 true，控制器回 304。
     * @param ifNoneMatch     请求头 If-None-Match 原值，可为 null
     * @param ifModifiedSince 请求头 If-Modified-Since 原值，可为 null
     * @return 文件存在即为 true（包括 304 的情况）
     */
    public boolean loadFile(String resourcePath, String ifNoneMatch, String ifModifiedSince) {
        content = null;
        etag = null;
        lastModified = 0;
        notModified = false;
        try {
            if (!stat(resourcePath)) return false;
            this.filePath = resourcePath;
            this.contentType = guessMimeType(resourcePath);
            if (matches(ifNoneMatch, ifModifiedSince)) {
                notModified = true;
                return true;
            }
            this.content = read(resourcePath);
            return true;
        } catch (IOException e) {
            pushError("file", "无法加载文件: " + e.getMessage());
            return false;
        }
    }

    /** 取元信息，填 etag 与 lastModified；文件不存在时 pushError 并返回 false */
    protected boolean stat(String resourcePath) throws IOException {
        Path path = Paths.get("resource").resolve(resourcePath);
        if (!Files.isRegularFile(path)) {
            pushError("file", "文件不存在: " + resourcePath);
            return false;
        }
        long size = Files.size(path);
        this.lastModified = Files.getLastModifiedTime(path).toMillis();
        this.etag = "\"" + Long.toHexString(size) + "-" + Long.toHexString(lastModified) + "\"";
        return true;
    }

    protected byte[] read(String resourcePath) throws IOException {
        return Files.readAllBytes(Paths.get("resource").resolve(resourcePath));
    }

    /** RFC 9110：有 If-None-Match 时只看它，忽略 If-Modified-Since；ETag 按弱比较（GET/HEAD 适用） */
    private boolean matches(String ifNoneMatch, String ifModifiedSince) {
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            String mine = stripWeak(etag);
            for (String tag : ifNoneMatch.split(",")) {
                String t = tag.trim();
                if (t.equals("*") || stripWeak(t).equals(mine)) return true;
            }
            return false;
        }
        if (ifModifiedSince != null && !ifModifiedSince.isBlank() && lastModified > 0) {
            try {
                long since = ZonedDateTime.parse(ifModifiedSince.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toEpochSecond();
                return lastModified / 1000 <= since;   // HTTP-date 只精确到秒
            } catch (DateTimeParseException ignore) {
                return false;   // 格式不对的头按没带处理
            }
        }
        return false;
    }

    private static String stripWeak(String tag) {
        return tag != null && tag.startsWith("W/") ? tag.substring(2) : tag;
    }

    public String getEtag() {
        return etag;
    }

    /** Last-Modified 响应头的值；未知时为 null */
    public String getLastModified() {
        return lastModified > 0 ? HTTP_DATE.format(Instant.ofEpochMilli(lastModified)) : null;
    }

    public boolean isNotModified() {
        return notModified;
    }

    protected String guessMimeType(String path) {
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
