package demo.Controller;

import top.x0a.tinyweb.Controller;
import top.x0a.tinyweb.FileServeModel;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态文件服务控制器：/Static/showDemo.html 等，文件在站点目录的 resource/static_pages/ 下
 * 使用字典映射来控制哪些文件可以被访问，增加安全性和灵活性
 */
public class Static extends Controller {

    // 文件映射表：访问名 -> 实际资源路径
    // 只有在这里明确注册的文件才能被访问
    private static final Map<String, String> FILE_MAP = new HashMap<>();
    static {
        FILE_MAP.put("showDemo.html", "static_pages/showDemo.html");
        FILE_MAP.put("showDemo.js", "static_pages/showDemo.js");
        // 未来可继续添加其他可访问的文件
    }

    @Override
    public void index() {
        // /Static/showDemo.html：第二段没有对应方法，框架回退到 index；params1 是第二段原文（不做 key-value 拆分）
        String file = ctx.param("params1", "");
        if (file.isEmpty()) {
            enableCors();
            status(400);
            echo("缺少文件名，用法: /Static/showDemo.html");
            return;
        }

        // 从映射表中查找实际的资源路径
        String resourcePath = FILE_MAP.get(file);
        if (resourcePath == null) {
            enableCors();
            status(404);
            echo("文件不存在或无访问权限: " + file);
            return;
        }

        FileServeModel fs = getModel(FileServeModel.class);
        if (!fs.loadFile(resourcePath, ctx.header("If-None-Match"), ctx.header("If-Modified-Since"))) {
            enableCors();
            status(500);
            echo("无法加载文件: " + resourcePath);
            return;
        }

        enableCors();
        header("ETag", fs.getEtag());
        if (fs.getLastModified() != null) header("Last-Modified", fs.getLastModified());
        // no-cache = 可以缓存，但每次先来问一声；不写的话浏览器会按 Last-Modified 自行估算新鲜期，改了文件也看不到
        header("Cache-Control", "no-cache");
        if (fs.isNotModified()) {
            status(304);
            return;
        }
        header("Content-Type", fs.getContentType());
        echo(new String(fs.getContent()));
    }

    /** 添加 CORS 响应头，允许跨域请求 */
    private void enableCors() {
        header("Access-Control-Allow-Origin", "*");
        header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        header("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
