package demo.Controller;

import demo.Model.Greeter;
import top.x0a.tinyweb.Controller;

/**
 * 示例控制器：controllerPackage=demo.Controller，路由 home -> demo.Controller.Home。
 * 这个文件只 import 了 top.x0a.tinyweb.Controller —— 它编译时的 classpath 里
 * 只有 tinyweb.jar，能编过就说明公开 API 面够用。
 */
public class Home extends Controller {

    @Override
    public void index() {
        echo("<h1>tinyWEB-java</h1><p>home/index</p>");
    }

    /** URL 段参数：/home/hello/name-world；query 参数：/home/hello?name=world */
    public void hello() {
        echo("hello from Home.hello, name=" + ctx.param("name"));
    }

    /** 用模型：类型安全的 getModel(Class)，模型不存在是编译期错误 */
    public void greet() {
        Greeter g = getModel(Greeter.class);
        echo(g.greet(ctx.param("name", "stranger")));
    }

    /** 状态码 + JSON */
    public void api() {
        status(201);
        json("{\"controller\":\"home\",\"action\":\"api\",\"ok\":true}");
    }

    /** 重定向 */
    public void old() {
        redirect("/home/index");
    }

    /** 会话：每次访问计数 +1，验证 Set-Cookie 与跨请求保持 */
    public void counter() {
        Object n = ctx.session().get("n");
        int next = (n instanceof Integer i ? i : 0) + 1;
        ctx.session().put("n", next);
        echo("count=" + next);
    }

    /** 表单 body（对照 $_POST） */
    public void submit() {
        echo("posted name=" + ctx.form("name"));
    }

    /** 验证 action 抛异常时的 callstack 非空日志 + 500 */
    public void boom() {
        throw new RuntimeException("intentional");
    }
}
