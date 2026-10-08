package top.x0a.tinyweb;

import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * JAR 文件服务模型：从 jar 包中读取文件
 * 用于提供 jar 包内的资源访问（无需解压）
 *
 * TODO: 待改进的架构问题
 * 1. 不应该在 loadFile 里硬编码判断是否为 jar，应该通过可配置的策略来决定
 * 2. 继承 FileServeModel 并没有好的封装，应该重新设计架构
 * 3. 缺少路径安全检查，防止恶意访问
 */
public class JarFileServeModel extends FileServeModel {

    /** jar 条目的元信息：ETag 用 大小 + CRC（jar 里现成的，内容变了 CRC 必变），Last-Modified 用条目时间 */
    @Override
    protected boolean stat(String resourcePath) throws IOException {
        String jarPath = selfJar();
        // 如果不是 jar 包（开发环境），降级到文件系统读取
        if (jarPath == null) return super.stat(resourcePath);

        try (JarFile jarFile = new JarFile(jarPath)) {
            JarEntry entry = jarFile.getJarEntry(resourcePath);
            if (entry == null || entry.isDirectory()) {
                pushError("jar_file", "jar 包中文件不存在: " + resourcePath);
                return false;
            }
            long time = entry.getTime();
            this.lastModified = time > 0 ? time : 0;
            long crc = entry.getCrc();
            String tail = crc >= 0 ? Long.toHexString(crc) : Long.toHexString(time);
            this.etag = "\"" + Long.toHexString(entry.getSize()) + "-" + tail + "\"";
            return true;
        }
    }

    @Override
    protected byte[] read(String resourcePath) throws IOException {
        String jarPath = selfJar();
        if (jarPath == null) return super.read(resourcePath);

        try (JarFile jarFile = new JarFile(jarPath)) {
            return jarFile.getInputStream(jarFile.getJarEntry(resourcePath)).readAllBytes();
        }
    }

    /** 本类所在的 jar；不在 jar 里（开发时直接跑 class 目录）返回 null */
    private static String selfJar() {
        String p = JarFileServeModel.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        return p.endsWith(".jar") ? p : null;
    }
}
