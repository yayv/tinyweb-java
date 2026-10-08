package demo.Controller;

import demo.Model.MUser;
import demo.TokenUtils;
import top.x0a.tinyweb.Controller;
import java.util.Map;

public class User extends Controller {

    @Override
    public void index() {
        echo("<h1>User Controller</h1><p>API endpoints: POST /user/login, POST /user/logout</p>");
    }

    public void login() {
        enableCors();
        String username = ctx.form("username");
        String password = ctx.form("password");

        MUser user = getModel(MUser.class);
        user.setUsername(username);
        user.setPassword(password);

        if (!user.validate()) {
            status(401);
            String message = "authentication failed";
            Map<String, Object> error = ctx.popError();
            if (error != null && error.containsKey("msg")) {
                Object msg = error.get("msg");
                if (msg != null) {
                    message = msg.toString();
                }
            }
            json("{\"ok\":false,\"message\":\"" + escapeJson(message) + "\"}");
            return;
        }

        String token = TokenUtils.generateToken(username);
        status(200);
        json("{\"ok\":true,\"token\":\"" + token + "\"}");
    }

    public void logout() {
        enableCors();
        status(200);
        json("{\"ok\":true,\"message\":\"logged out\"}");
    }

    private void enableCors() {
        header("Access-Control-Allow-Origin", "*");
        header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        header("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
