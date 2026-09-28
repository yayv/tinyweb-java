package demo.Controller;

import demo.Model.JarFileServe;
import top.x0a.tinyweb.Controller;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态文件服务控制器：/static/index?f=showDemo 等
 * 使用字典映射来控制哪些文件可以被访问，增加安全性和灵活性
 */
public class Static extends Controller {

    // 文件映射表：访问名 -> 实际资源路径
    // 只有在这里明确注册的文件才能被访问
    private static final Map<String, String> FILE_MAP = new HashMap<>();
    static {
        FILE_MAP.put("showDemo", "static_pages/showDemo.html");
        FILE_MAP.put("showDemo.js", "static_pages/showDemo.js");
        // 未来可继续添加其他可访问的文件
    }

    @Override
    public void index() {
        String file = ctx.param("f", "");
        if (file.isEmpty()) {
            enableCors();
            status(400);
            echo("缺少 f 参数，用法: /static/index?f=showDemo");
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

        JarFileServe jarFs = getModel(JarFileServe.class);
        if (!jarFs.loadFile(resourcePath)) {
            enableCors();
            status(500);
            echo("无法加载文件: " + resourcePath);
            return;
        }

        enableCors();
        header("Content-Type", jarFs.getContentType());
        echo(new String(jarFs.getContent()));
    }

    /** 添加 CORS 响应头，允许跨域请求 */
    private void enableCors() {
        header("Access-Control-Allow-Origin", "*");
        header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        header("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
