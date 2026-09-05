package top.x0a.tinyweb;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SessionStore — 内存会话存储。对照 PHP session_start()（session_id 走 cookie）。
 *
 * 相比最初的版本修了两点：
 *  - <b>空闲过期</b>：原实现的 ConcurrentHashMap 只增不删，长驻进程里是确定的内存泄漏。
 *    这里给每个 Session 记 lastAccess，超过 maxIdle 即回收；清扫是访问时按时间门限触发的
 *    惰性全表扫描，不额外起线程（也就没有关不掉的守护线程）。
 *  - <b>不接受未知的客户端 session id</b>：客户端带来的 id 若不在表里（伪造或已过期），
 *    重新签发一个随机 id，而不是拿它建会话——否则攻击者可预先固定受害者的 session id
 *    （session fixation）。id 用 SecureRandom 而非 UUID.randomUUID 的字符串。
 *
 * 生产可换 Redis / 文件存储，把本类替换掉即可，Session 的接口不变。
 */
final class SessionStore {
    private static final long SWEEP_INTERVAL_NANOS = 60L * 1_000_000_000L;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long maxIdleNanos;
    private final AtomicLong nextSweepNanos = new AtomicLong(System.nanoTime() + SWEEP_INTERVAL_NANOS);

    SessionStore(long maxIdleNanos) {
        this.maxIdleNanos = maxIdleNanos;
    }

    /**
     * 取会话。sid 命中且未过期则复用（并刷新 lastAccess），否则签发新 id。
     * 调用方通过比对返回的 {@link Session#id()} 与传入 sid 判断是否需要下发 Set-Cookie。
     */
    Session start(String sid) {
        long now = System.nanoTime();
        sweepIfDue(now);

        if (sid != null && !sid.isEmpty()) {
            Session s = sessions.get(sid);
            if (s != null) {
                if (!s.expired(now, maxIdleNanos)) {
                    s.touch(now);
                    return s;
                }
                sessions.remove(sid, s);
            }
        }
        Session fresh = new Session(newId(), now);
        sessions.put(fresh.id(), fresh);
        return fresh;
    }

    int size() { return sessions.size(); }

    private void sweepIfDue(long now) {
        long due = nextSweepNanos.get();
        if (now < due) return;
        if (!nextSweepNanos.compareAndSet(due, now + SWEEP_INTERVAL_NANOS)) return;  // 只让一个线程扫
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            if (it.next().expired(now, maxIdleNanos)) it.remove();
        }
    }

    private String newId() {
        byte[] buf = new byte[16];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
