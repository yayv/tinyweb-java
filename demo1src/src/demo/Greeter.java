package demo;

import top.x0a.tinyweb.Model;

/** 示例模型：由 Controller.getModel(Greeter.class) 构造并 initialize */
public class Greeter extends Model {
    public String greet(String name) {
        if (name == null || name.isBlank()) {
            pushError(name, "name 为空");   // 对照 model::pushError，落在请求级错误栈
            return "hello, ?";
        }
        return "hello, " + name;
    }
}
