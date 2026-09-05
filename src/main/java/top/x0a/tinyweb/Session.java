package top.x0a.tinyweb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session — 单个会话的数据袋。对照 PHP $_SESSION。
 * lastAccess 由 SessionStore 维护，用于空闲过期回收。
 */
public final class Session {
    private final String id;
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private volatile long lastAccessNanos;

    Session(String id, long nowNanos) {
        this.id = id;
        this.lastAccessNanos = nowNanos;
    }

    public String id() { return id; }

    public Object get(String key) { return data.get(key); }

    public void put(String key, Object value) { data.put(key, value); }

    public Object remove(String key) { return data.remove(key); }

    /** 直接暴露底层 map（ConcurrentHashMap），供需要批量操作的业务代码使用 */
    public Map<String, Object> data() { return data; }

    void touch(long nowNanos) { this.lastAccessNanos = nowNanos; }

    boolean expired(long nowNanos, long maxIdleNanos) {
        return nowNanos - lastAccessNanos > maxIdleNanos;
    }
}
