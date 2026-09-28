package demo.Model;

import top.x0a.tinyweb.Model;

public class MUser extends Model {
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "test123";

    private String username;
    private String password;

    public MUser() {
    }

    public MUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean validate() {
        if (username == null || username.isBlank()) {
            pushError("username", "用户名不能为空");
            return false;
        }
        if (password == null || password.isBlank()) {
            pushError("password", "密码不能为空");
            return false;
        }
        if (!VALID_USERNAME.equals(username) || !VALID_PASSWORD.equals(password)) {
            pushError("auth", "用户名或密码错误");
            return false;
        }
        return true;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
