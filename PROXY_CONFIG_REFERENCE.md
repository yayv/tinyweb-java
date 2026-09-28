# 反向代理配置参考

当部署到生产环境时，通常在前端放置一个 Web 服务器（如 nginx 或 Apache）作为反向代理，这样可以：
- 把所有请求路由到后端 tinyWEB 服务
- 避免跨域问题（前端和后端同源）
- 提供 SSL/TLS 支持
- 做负载均衡

## Nginx 配置

```nginx
server {
    listen 80;
    server_name example.com;

    # 重定向到 HTTPS（可选）
    # return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;  # 可选：HTTPS
    server_name example.com;

    # SSL 证书配置（可选）
    # ssl_certificate /path/to/cert.pem;
    # ssl_certificate_key /path/to/key.pem;

    # 反向代理到后端 tinyWEB 服务
    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        
        # 传递原始请求信息
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持（如需要）
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 静态文件缓存（可选）
    location ~ \.(js|css|html|png|jpg|jpeg|gif|svg|woff|woff2)$ {
        proxy_pass http://localhost:8080;
        proxy_cache_valid 200 1d;
        add_header Cache-Control "public, max-age=86400";
    }
}
```

## Apache 配置

```apache
<VirtualHost *:80>
    ServerName example.com
    
    # 重定向到 HTTPS（可选）
    # Redirect permanent / https://example.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName example.com
    
    # SSL 配置（可选）
    # SSLEngine on
    # SSLCertificateFile /path/to/cert.pem
    # SSLCertificateKeyFile /path/to/key.pem

    # 启用必要的模块
    # mod_proxy 和 mod_proxy_http
    
    # 反向代理所有请求到后端 tinyWEB 服务
    ProxyRequests Off
    ProxyPreserveHost On
    
    <Proxy *>
        Order allow,deny
        Allow from all
    </Proxy>
    
    ProxyPass / http://localhost:8080/
    ProxyPassReverse / http://localhost:8080/
    
    # 传递原始请求头
    RequestHeader set X-Forwarded-Proto "https"
    RequestHeader set X-Forwarded-For "%{REMOTE_ADDR}s"
</VirtualHost>
```

## 注意事项

1. **多个后端实例**：上面的配置可以扩展为负载均衡
   - Nginx：使用 upstream 块定义后端池
   - Apache：使用多个 ProxyPass 指令

2. **会话保持**：
   - 使用 sticky session 或 IP Hash 确保同一用户请求到同一后端实例
   - 或使用分布式会话存储（Redis 等）

3. **跨域处理**：
   - 使用反向代理时，前端和后端同源，不存在跨域问题
   - 不需要在 tinyWEB 中添加 CORS 头

4. **性能优化**：
   - 启用缓存
   - 启用 gzip 压缩
   - 调整连接池大小

## 开发环境 vs 生产环境

- **开发**：直接访问 `http://localhost:8080`，API 中处理 CORS（见 demo1）
- **生产**：通过 nginx/Apache 反向代理，前端和后端同源，无需 CORS
