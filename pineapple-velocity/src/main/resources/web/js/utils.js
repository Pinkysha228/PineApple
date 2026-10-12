/* ==========================================================================
   PineApple Web Monitor - UI Utilities, Notifications & Theme Manager
   ========================================================================== */

function showToast(type, message, duration = 3800) {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let iconClass = 'fa-circle-info';
    if (type === 'success') iconClass = 'fa-circle-check';
    else if (type === 'error') iconClass = 'fa-circle-exclamation';
    else if (type === 'warning') iconClass = 'fa-triangle-exclamation';

    toast.innerHTML = `
        <div class="toast-icon"><i class="fa-solid ${iconClass}"></i></div>
        <div class="toast-content"></div>
        <button class="toast-close" aria-label="Close" onclick="this.parentElement.remove()">&times;</button>
    `;
    // Messages can echo user input or server text, so never parse them as HTML.
    toast.querySelector('.toast-content').textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(40px)';
        setTimeout(() => toast.remove(), 320);
    }, duration);
}

function getMetricState(metric, value) {
    if (metric === 'tps') {
        if (value >= 19.0) return 'good';
        if (value >= 15.0) return 'warn';
        return 'danger';
    }
    if (metric === 'ram' || metric === 'cpu') {
        if (value >= 85.0) return 'danger';
        if (value >= 70.0) return 'warn';
        return 'good';
    }
    return 'good';
}

function getStateTheme(state, defaultAccent = '#3b82f6') {
    if (state === 'danger') {
        return { color: '#ef4444', textClass: 'text-red', fillClass: 'fill-danger', grad: 'rgba(239, 68, 68, 0.12)' };
    }
    if (state === 'warn') {
        return { color: '#f59e0b', textClass: 'text-yellow', fillClass: 'fill-warning', grad: 'rgba(245, 158, 11, 0.12)' };
    }
    if (defaultAccent === '#10b981') {
        return { color: '#10b981', textClass: 'text-green', fillClass: 'fill-green', grad: 'rgba(16, 185, 129, 0.12)' };
    }
    if (defaultAccent === '#8b5cf6' || defaultAccent === '#7c3aed') {
        return { color: '#8b5cf6', textClass: 'text-purple', fillClass: 'fill-purple', grad: 'rgba(139, 92, 246, 0.12)' };
    }
    return { color: '#3b82f6', textClass: 'text-blue', fillClass: 'fill-blue', grad: 'rgba(59, 130, 246, 0.12)' };
}


function triggerFormError(form, inputToHighlight) {
    if (form) {
        form.classList.remove('form-shake');
        void form.offsetWidth;
        form.classList.add('form-shake');
        setTimeout(() => form.classList.remove('form-shake'), 500);
    }
    if (inputToHighlight) {
        inputToHighlight.classList.add('input-error');
        inputToHighlight.focus();
        setTimeout(() => inputToHighlight.classList.remove('input-error'), 1800);
    }
}

function resetSubmitButton(btn, iconClass) {
    if (!btn) return;
    btn.classList.remove('loading');
    const icon = btn.querySelector('.btn-icon i');
    if (icon) icon.className = `fa-solid ${iconClass}`;
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;')
                      .replace(/</g, '&lt;')
                      .replace(/>/g, '&gt;')
                      .replace(/"/g, '&quot;')
                      .replace(/'/g, '&#039;');
}



function togglePasswordVisibility(fieldId, btn) {
    const input = document.getElementById(fieldId);
    if (!input) return;
    const isPassword = input.type === 'password';
    input.type = isPassword ? 'text' : 'password';
    const icon = btn.querySelector('i');
    if (icon) {
        icon.className = isPassword ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    }
}

function getStoredTheme() {
    const saved = localStorage.getItem('pineapple_theme');
    if (saved === 'dark' || saved === 'light') return saved;
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return 'dark';
    }
    return 'light';
}

function applyTheme(theme) {
    if (theme === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
        document.body.classList.add('dark-theme');
    } else {
        document.documentElement.removeAttribute('data-theme');
        document.body.classList.remove('dark-theme');
    }
    localStorage.setItem('pineapple_theme', theme);

    document.querySelectorAll('.theme-icon').forEach(icon => {
        icon.className = theme === 'dark' ? 'fa-solid fa-sun theme-icon' : 'fa-solid fa-moon theme-icon';
    });

    ['tps', 'ram', 'cpu'].forEach(metric => {
        const cap = metric.charAt(0).toUpperCase() + metric.slice(1);
        const card = document.getElementById('card' + cap);
        if (card && card.classList.contains('flipped')) {
            renderMetricChart(metric);
        }
    });
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    const next = current === 'dark' ? 'light' : 'dark';
    applyTheme(next);
}
window.toggleTheme = toggleTheme;


window.showToast = showToast;
window.getMetricState = getMetricState;
window.getStateTheme = getStateTheme;
window.triggerFormError = triggerFormError;
window.resetSubmitButton = resetSubmitButton;
window.escapeHtml = escapeHtml;
window.togglePasswordVisibility = togglePasswordVisibility;
window.getStoredTheme = getStoredTheme;
window.applyTheme = applyTheme;
window.toggleTheme = toggleTheme;
