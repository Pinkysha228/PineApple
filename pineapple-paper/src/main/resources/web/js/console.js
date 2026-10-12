/* ==========================================================================
   PineApple Web Monitor - Server Console Terminal & Live Stream
   ========================================================================== */

let consolePollInterval = null;
let consoleAutoscroll = true;
let lastLogHash = '';

function checkServerConsolePermissions(serverId) {
    const consoleCard = document.getElementById('serverConsoleCard');
    const splitContainer = document.getElementById('serverBottomSplit');
    const commandBar = document.getElementById('consoleCommandBar');
    const consoleSub = document.getElementById('consoleSubtitle');

    if (!currentUser || !serverId) {
        if (consoleCard) consoleCard.style.display = 'none';
        if (splitContainer) splitContainer.classList.add('no-console');
        stopConsolePolling();
        return;
    }

    const isSuper = currentUser.is_superadmin === true;
    const perms = (currentUser.permissions && currentUser.permissions[serverId]) || {};
    const canView = isSuper || perms.console_view === true;
    const canCommand = isSuper || perms.console_command === true;

    if (canView) {
        if (consoleCard) consoleCard.style.display = 'flex';
        if (splitContainer) splitContainer.classList.remove('no-console');
        if (commandBar) commandBar.style.display = canCommand ? 'flex' : 'none';
        if (consoleSub) consoleSub.textContent = `${serverId.toUpperCase()} • Live Terminal`;
        startConsolePolling(serverId);
    } else {
        if (consoleCard) consoleCard.style.display = 'none';
        if (splitContainer) splitContainer.classList.add('no-console');
        stopConsolePolling();
    }
}

function startConsolePolling(serverId) {
    stopConsolePolling();
    fetchConsoleLogs(serverId);
    consolePollInterval = setInterval(() => {
        if (currentView === 'server' && activeServerId === serverId) {
            fetchConsoleLogs(serverId);
        }
    }, 2500);
}

function stopConsolePolling() {
    if (consolePollInterval) {
        clearInterval(consolePollInterval);
        consolePollInterval = null;
    }
}

async function fetchConsoleLogs(serverId) {
    if (!serverId) return;
    try {
        const res = await fetch(`/api/console/logs?server=${encodeURIComponent(serverId)}`);
        if (res.ok) {
            const data = await res.json();
            if (data.status === 'ok' && Array.isArray(data.logs)) {
                renderConsoleLogs(data.logs);
            }
        }
    } catch (e) {
        // Silently catch poll errors
    }
}

function renderConsoleLogs(logs) {
    const container = document.getElementById('serverConsoleLogs');
    if (!container) return;

    const hash = logs.join('\n');
    if (hash === lastLogHash) return;
    lastLogHash = hash;

    if (logs.length === 0) {
        container.innerHTML = '<div class="console-line text-muted">[PineApple] Ожидание сообщений консоли сервера...</div>';
        return;
    }

    container.innerHTML = '';
    logs.forEach(line => {
        const lineEl = document.createElement('div');
        lineEl.className = 'console-line';
        if (line.includes('ERROR') || line.includes('Exception') || line.includes('FATAL')) {
            lineEl.classList.add('log-error');
        } else if (line.includes('WARN')) {
            lineEl.classList.add('log-warn');
        } else if (line.startsWith('>>>') || line.includes('[Command]')) {
            lineEl.classList.add('log-cmd');
        } else {
            lineEl.classList.add('log-info');
        }
        lineEl.textContent = line;
        container.appendChild(lineEl);
    });

    if (consoleAutoscroll) {
        container.scrollTop = container.scrollHeight;
    }
}

function clearConsoleDisplay() {
    const container = document.getElementById('serverConsoleLogs');
    if (container) {
        container.innerHTML = '<div class="console-line text-muted">[PineApple] Экран консоли очищен.</div>';
    }
}
window.clearConsoleDisplay = clearConsoleDisplay;

function toggleConsoleAutoscroll() {
    consoleAutoscroll = !consoleAutoscroll;
    const btn = document.getElementById('btnConsoleAutoscroll');
    if (btn) {
        btn.classList.toggle('active', consoleAutoscroll);
    }
    showToast('info', consoleAutoscroll ? 'Автопрокрутка включена' : 'Автопрокрутка выключена');
}
window.toggleConsoleAutoscroll = toggleConsoleAutoscroll;

function handleConsoleInputKeyDown(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        sendConsoleCommand();
    }
}
window.handleConsoleInputKeyDown = handleConsoleInputKeyDown;

async function sendConsoleCommand() {
    const input = document.getElementById('consoleCmdInput');
    if (!input || !activeServerId) return;
    const command = input.value.trim();
    if (!command) return;

    input.value = '';

    // Optimistically echo to terminal
    const container = document.getElementById('serverConsoleLogs');
    if (container) {
        const echoEl = document.createElement('div');
        echoEl.className = 'console-line log-cmd';
        echoEl.textContent = `>>> ${command}`;
        container.appendChild(echoEl);
        if (consoleAutoscroll) container.scrollTop = container.scrollHeight;
    }

    try {
        const res = await fetch('/api/console/command', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                server: activeServerId,
                command: command
            })
        });
        const data = await res.json();
        if (res.ok && data.status === 'ok') {
            showToast('success', `Команда отправлена: ${command}`);
        } else {
            showToast('error', data.message || 'Ошибка выполнения команды');
        }
    } catch (e) {
        showToast('error', 'Сетевая ошибка отправки команды');
    }
}
window.sendConsoleCommand = sendConsoleCommand;

window.checkServerConsolePermissions = checkServerConsolePermissions;
window.startConsolePolling = startConsolePolling;
window.stopConsolePolling = stopConsolePolling;
window.renderConsoleLogs = renderConsoleLogs;
window.clearConsoleDisplay = clearConsoleDisplay;
window.toggleConsoleAutoscroll = toggleConsoleAutoscroll;
window.handleConsoleInputKeyDown = handleConsoleInputKeyDown;
window.sendConsoleCommand = sendConsoleCommand;
