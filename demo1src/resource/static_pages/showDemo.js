const DEFAULT_API_HOST = 'http://localhost:8080';
let API_HOST = DEFAULT_API_HOST;

// ========== 配置 ==========

function setApiHost() {
    const host = document.getElementById('api-host-input').value.trim();
    if (!host) {
        showMessage('api-config-message', '请输入有效的 API 地址', 'error');
        return;
    }

    API_HOST = host;
    localStorage.setItem('api_host', host);
    showMessage('api-config-message', `✓ API 地址已设置: ${API_HOST}`, 'success');
}

function resetApiHost() {
    API_HOST = DEFAULT_API_HOST;
    document.getElementById('api-host-input').value = DEFAULT_API_HOST;
    localStorage.removeItem('api_host');
    showMessage('api-config-message', `✓ API 地址已重置为默认值: ${DEFAULT_API_HOST}`, 'success');
}

// ========== Demo1 ==========

function submitDemo1Form() {
    const name = document.getElementById('demo1-name').value.trim();
    if (!name) {
        showMessage('demo1-message', '请输入名字', 'error');
        return;
    }

    fetch(`${API_HOST}/home/submit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `name=${encodeURIComponent(name)}`
    })
        .then(r => r.text())
        .then(text => {
            showMessage('demo1-message', `返回: ${text}`, 'success');
        })
        .catch(e => showMessage('demo1-message', `错误: ${e.message}`, 'error'));
}

function demo1Login() {
    const username = document.getElementById('demo1-username').value.trim();
    const password = document.getElementById('demo1-password').value.trim();

    if (!username || !password) {
        showMessage('demo1-message', '请输入用户名和密码', 'error');
        return;
    }

    fetch(`${API_HOST}/user/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
    })
        .then(r => r.json())
        .then(data => {
            if (data.ok) {
                showMessage('demo1-message', `登录成功！Token: ${data.token}`, 'success');
            } else {
                showMessage('demo1-message', `登录失败: ${data.message}`, 'error');
            }
        })
        .catch(e => showMessage('demo1-message', `错误: ${e.message}`, 'error'));
}

function demo1Counter() {
    fetch(`${API_HOST}/home/counter`)
        .then(r => r.text())
        .then(text => {
            showMessage('demo1-message', text, 'success');
        })
        .catch(e => showMessage('demo1-message', `错误: ${e.message}`, 'error'));
}

function demo1Greet() {
    const name = document.getElementById('demo1-greet-name').value.trim() || 'stranger';

    fetch(`${API_HOST}/home/greet?name=${encodeURIComponent(name)}`)
        .then(r => r.text())
        .then(text => {
            showMessage('demo1-message', text, 'success');
        })
        .catch(e => showMessage('demo1-message', `错误: ${e.message}`, 'error'));
}

// ========== Demo2 ==========

function submitDemo2Form() {
    showMessage('demo2-message', 'Demo2 功能待实现', 'info');
}

// ========== 辅助函数 ==========

function showMessage(elementId, message, type) {
    const el = document.getElementById(elementId);
    el.textContent = message;
    el.className = `message message-${type}`;
    el.style.display = 'block';
}

function clearMessage(elementId) {
    const el = document.getElementById(elementId);
    el.textContent = '';
    el.style.display = 'none';
}

// 页面加载时设置
document.addEventListener('DOMContentLoaded', function() {
    // 从 localStorage 恢复之前保存的 API_HOST
    const savedHost = localStorage.getItem('api_host');
    if (savedHost) {
        API_HOST = savedHost;
        document.getElementById('api-host-input').value = savedHost;
    } else {
        document.getElementById('api-host-input').value = DEFAULT_API_HOST;
    }
    console.log('Demo 页面已加载，API_HOST: ' + API_HOST);
});
