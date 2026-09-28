package top.x0a.tinyweb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * HttpServer — 端口监听 + 轮询，Java 21 虚拟线程每连接（框架内部）。
 *
 * 这是 PHP 版没有的部分：PHP 靠 web 服务器（FastCGI / FPM）实现"每请求一进程"的隔离，
 * 这里用一个长驻进程 + 每连接一个虚拟线程复现同样的隔离模型：
 *   - ServerSocket.accept() 阻塞轮询新连接
 *   - 每个连接交给 newVirtualThreadPerTaskExecutor 的一个虚拟线程处理
 *   - 虚拟线程里写朴素阻塞式 I/O，却能扛住海量并发（Java 21 Loom）
 *   - 每请求一个 Context，天然线程隔离，正是 PHP 每请求进程模型的还魂
 *
 * 为什么不用 NIO Selector 事件循环：它与"每请求独立上下文"的模型相冲、代码复杂；
 * Loom 让阻塞式写法在这种负载下同样高吞吐。
 *
 * 只做最小 HTTP/1.1 解析（零依赖，手写）：请求行 + 头 + Content-Length body，逐连接 close。
 */
class HttpServer {
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final int port;
    /** 单站点是 FrontController::handle，多站点是 Tools 按 Host 选站的分派 */
    private final Consumer<Context> handler;

    private volatile ServerSocket serverSocket;
    private volatile int boundPort = -1;

    HttpServer(int port, Consumer<Context> handler) {
        this.port = port;
        this.handler = handler;
    }

    int boundPort() { return boundPort; }

    /** 阻塞直到 stop() 或 accept 出错 */
    void start() throws IOException {
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try (ServerSocket server = new ServerSocket()) {
            this.serverSocket = server;
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            this.boundPort = server.getLocalPort();
            System.out.println("tinyWEB-java listening on :" + boundPort
                    + " (virtual-thread-per-connection)");
            while (!Thread.currentThread().isInterrupted()) {
                Socket sock;
                try {
                    sock = server.accept();
                } catch (IOException e) {
                    if (server.isClosed()) break;   // stop() 关闭监听套接字：正常收摊
                    throw e;
                }
                workers.submit(() -> handleConnection(sock));
            }
        } finally {
            workers.shutdown();
        }
    }

    void stop() {
        ServerSocket s = serverSocket;
        if (s != null) {
            try { s.close(); } catch (IOException ignore) { }
        }
    }

    protected void handleConnection(Socket sock) {
        try (Socket s = sock;
             InputStream in = new BufferedInputStream(s.getInputStream());
             OutputStream out = new BufferedOutputStream(s.getOutputStream())) {
            s.setSoTimeout(15_000);

            Context ctx = new Context();
            if (!parseRequest(in, ctx)) {
                writeError(out, 400, "bad request");
                return;
            }
            selectHandler(ctx).accept(ctx);
            writeResponse(out, ctx);
        } catch (IOException e) {
            // 连接层异常，放弃该连接
        }
    }

    /** 根据 Context 选择对应的 handler。子类可覆盖以支持多站点 */
    protected Consumer<Context> selectHandler(Context ctx) {
        return handler;
    }

    /** 最小 HTTP/1.1 解析：请求行 + headers + query string + Content-Length body */
    protected boolean parseRequest(InputStream in, Context ctx) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return false;
        String[] rl = requestLine.split(" ");
        if (rl.length < 2) return false;
        ctx.setMethod(rl[0]);
        ctx.setUri(rl[1]);

        // query string -> params（对照 $_GET）。原实现从未解析过 query，
        // 导致 FrontController 的 ?controller=a&action=b 分支是死代码。
        int q = rl[1].indexOf('?');
        if (q >= 0) {
            Context.parseUrlEncoded(rl[1].substring(q + 1), ctx.mutableParams());
        }

        int contentLength = 0;
        int headerBytes = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) return false;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            ctx.putHeader(name, value);
            if (name.equals("host")) ctx.setHost(value);
            if (name.equals("content-length")) {
                try { contentLength = Integer.parseInt(value); } catch (NumberFormatException ignore) { }
            }
        }

        if (contentLength > MAX_BODY_BYTES) return false;
        if (contentLength > 0) {                     // 对照 php://input
            byte[] buf = in.readNBytes(contentLength);
            ctx.setBody(new String(buf, StandardCharsets.UTF_8));
        }
        return true;
    }

    /** 逐字节读一行，去掉行尾 CRLF（不引 BufferedReader，避免打乱后续 body 字节流） */
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') break;
            buf.write(b);
        }
        if (!any) return null;
        byte[] bytes = buf.toByteArray();
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') len--;   // strip trailing CR
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    /** 把 Context 上攒的状态码 / 头 / cookie / 正文写成一个响应 */
    private void writeResponse(OutputStream out, Context ctx) throws IOException {
        byte[] payload = ctx.responseBody().getBytes(StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder()
                .append("HTTP/1.1 ").append(ctx.status()).append(' ')
                .append(reason(ctx.status())).append("\r\n")
                .append("Content-Type: ").append(ctx.responseContentType()).append("\r\n")
                .append("Content-Length: ").append(payload.length).append("\r\n");
        for (Map.Entry<String, String> e : ctx.responseHeaders().entrySet()) {
            header.append(sanitize(e.getKey())).append(": ").append(sanitize(e.getValue())).append("\r\n");
        }
        for (String c : ctx.responseCookies()) {
            header.append("Set-Cookie: ").append(sanitize(c)).append("\r\n");
        }
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    private void writeError(OutputStream out, int code, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + reason(code) + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    /** 掐掉 CR/LF，防止业务写入的头值把响应头劈成两段（response splitting） */
    private static String sanitize(String s) {
        return s == null ? "" : s.replace("\r", "").replace("\n", "");
    }

    private static String reason(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 421 -> "Misdirected Request";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default  -> code < 400 ? "OK" : "Error";
        };
    }
}
