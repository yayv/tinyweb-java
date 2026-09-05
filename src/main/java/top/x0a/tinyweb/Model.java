package top.x0a.tinyweb;

/**
 * Model — 模型基类。对照 model.php。
 *
 * 移植取舍：PHP 的 static $_error 在长驻多线程服务器里会跨请求串数据 + 竞态，
 * 已下沉到 Context（每请求隔离），因此 pushError / popError 通过 Context 走。
 * initalize（PHP 拼写笔误）此处更正为 initialize，并多带一个 Context 以便走请求级错误栈；
 * 它由框架的 getModel 调用，业务不直接调，故为包内可见。
 */
public abstract class Model {
    protected Config config;
    protected Object db;
    protected Context ctx;

    /** 对照 model::initalize($config,$db) */
    void initialize(Config config, Object db, Context ctx) {
        this.config = config;
        this.db = db;
        this.ctx = ctx;
    }

    protected void pushError(Object params, String msg) { ctx.pushError(params, msg); }

    protected Object popError() { return ctx.popError(); }
}
