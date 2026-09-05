package top.x0a.tinyweb;

/**
 * Controller — 控制器基类，业务项目 import 这个 jar 后要 extends 的东西。
 * 对照 controller.php 的 abstract class Controller。
 *
 * 移植取舍：
 *  - PHP 的 mo / emptymodel 透明代理（__call 计时日志 + 抓意外输出）已丢弃：
 *    Java 模型不 echo，抓输出无意义；计时日志不值得引字节码库做反射代理（破坏零依赖）。
 *  - 同理 getModel(String) + EmptyModel 也已删除：静态类型下"返回空壳让调用方判空"
 *    既不透明也不安全。只保留 getModel(Class)，模型不存在是编译期错误，不是线上 NPE。
 *  - PHP 控制器直接 echo，这里改为 echo(...) 写入 Context 的响应缓冲。
 *
 * 可当 action 派发的，只有子类自己声明的 public 无参方法；本类的成员一律 protected，
 * 因此不会被 URL 直接调到。
 */
public abstract class Controller {
    /** 对照 $_db：约定不希望 controller 直接访问，故用带下划线的名字 */
    protected Object _db;

    /** 请求级上下文：请求输入、响应构造、日志都在这上面 */
    protected Context ctx;
    /** 当前 host 的配置快照（只读） */
    protected Config config;

    /** 派发前由 FrontController 注入请求级依赖（框架内部，业务不可见） */
    void bind(Context ctx, Config config, Object db) {
        this.ctx = ctx;
        this.config = config;
        this._db = db;
    }

    /** 对照 echo：写入响应缓冲 */
    protected void echo(String s) { ctx.write(s); }

    /** 设置响应状态码 */
    protected void status(int code) { ctx.status(code); }

    /** 设置响应头 */
    protected void header(String name, String value) { ctx.setHeader(name, value); }

    /** 正文置为 JSON（框架零依赖，序列化由调用方负责） */
    protected void json(String jsonText) { ctx.json(jsonText); }

    /** 302 重定向 */
    protected void redirect(String location) { ctx.redirect(location); }

    /**
     * 对照 controller::getModel()，类型安全版：编译期确定模型类型，
     * 反射只用于无参构造，不按名字解析类。
     */
    protected final <T extends Model> T getModel(Class<T> type) {
        try {
            T m = type.getDeclaredConstructor().newInstance();
            m.initialize(config, _db, ctx);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "getModel(" + type.getName() + ") failed: 需要一个 public 无参构造", e);
        }
    }

    /** 对照 controller::missing()：脚手架提示（写入响应缓冲） */
    protected void missing(String controller, String action) {
        if (action == null || action.isEmpty())
            echo("控制器 " + controller + " 不存在\n");
        else
            echo("控制器方法 " + action + " 不存在\n");
    }

    /** 对照 abstract function index() */
    public abstract void index();
}
