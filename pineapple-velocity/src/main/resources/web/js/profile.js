/* ==========================================================================
   PineApple Web Monitor - User Profile, Session & Permissions Matrix
   ========================================================================== */

let currentUser = null;

function initProfileView() {
    const heroAuth = document.getElementById('heroAuthContent');
    const heroProfile = document.getElementById('heroProfileContent');
    const authView = document.getElementById('authViewContainer');
    const profileView = document.getElementById('profileViewContainer');
    const canvaWrapper = document.getElementById('canvaWrapper');

    if (heroAuth) heroAuth.style.display = 'none';
    if (heroProfile) heroProfile.style.display = 'block';
    if (authView) authView.style.display = 'none';
    if (profileView) profileView.style.display = 'block';

    const isReverseTransition = sessionStorage.getItem('pineapple_reverse_profile') === 'true' ||
                                document.documentElement.classList.contains('profile-reverse-active');

    if (isReverseTransition) {
        window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
        document.documentElement.classList.add('auth-transition-active', 'profile-reverse-active');
        document.body.classList.add('auth-transition-active');

        if (canvaWrapper) {
            canvaWrapper.classList.add('profile-reverse-entering');
        }

        // Force browser reflow to register the transition state
        void (canvaWrapper ? canvaWrapper.offsetHeight : document.body.offsetHeight);
        // Release static paint blocker from <head>
        document.documentElement.classList.remove('profile-entering-root');

        setTimeout(() => {
            if (canvaWrapper) {
                canvaWrapper.classList.remove('profile-reverse-entering');
            }
            document.documentElement.classList.remove('auth-transition-active', 'profile-reverse-active', 'profile-entering-root');
            document.body.classList.remove('auth-transition-active');
            sessionStorage.removeItem('pineapple_reverse_profile');
        }, 1100);
    } else {
        document.documentElement.classList.remove('profile-entering-root', 'profile-reverse-active', 'auth-transition-active');
        document.body.classList.remove('auth-transition-active');
    }

    loadCurrentUser();
}
window.initProfileView = initProfileView;

function openProfileView() {
    const dashboardPage = document.querySelector('.dashboard-page');
    sessionStorage.setItem('pineapple_reverse_profile', 'true');

    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.documentElement.classList.add('auth-transition-active');
    document.body.classList.add('auth-transition-active');

    if (dashboardPage) {
        const header = dashboardPage.querySelector('.dashboard-curved-header');
        if (header) {
            const h = header.offsetHeight;
            header.style.setProperty('--target-header-height', h + 'px');
            dashboardPage.style.setProperty('--target-header-height', h + 'px');
        }

        dashboardPage.classList.add('dashboard-reverse-morphing');
        setTimeout(() => {
            window.location.href = '/profile';
        }, 1000);
    } else {
        window.location.href = '/profile';
    }
}
window.openProfileView = openProfileView;

function returnToDashboardFromProfile() {
    sessionStorage.removeItem('pineapple_reverse_profile');
    document.documentElement.classList.remove('profile-reverse-active', 'profile-entering-root');
    triggerCinematicTransition('/dashboard', 'Возврат в дешборд');
}
window.returnToDashboardFromProfile = returnToDashboardFromProfile;

async function handleSeamlessLogout() {
    const heroAuth = document.getElementById('heroAuthContent');
    const heroProfile = document.getElementById('heroProfileContent');
    const authView = document.getElementById('authViewContainer');
    const profileView = document.getElementById('profileViewContainer');

    // 1. Trigger seamless slide animations
    if (heroProfile) heroProfile.classList.add('profile-slide-out-left');
    if (heroAuth) {
        heroAuth.style.display = 'block';
        heroAuth.classList.add('auth-slide-in-right');
    }
    if (profileView) profileView.classList.add('profile-slide-out-down');
    if (authView) {
        authView.style.display = 'block';
        authView.classList.add('auth-slide-in-up');
    }

    // 2. Perform logout request
    try {
        await fetch('/logout', { method: 'POST' });
    } catch (e) {
        try { await fetch('/logout'); } catch (_) {}
    }

    // 3. Clear session storage
    sessionStorage.removeItem('pineapple_auth_transition');
    sessionStorage.removeItem('pineapple_auth_msg');
    sessionStorage.removeItem('pineapple_reverse_profile');

    // 4. After animation finishes, clean up classes and display login
    setTimeout(() => {
        if (heroProfile) {
            heroProfile.style.display = 'none';
            heroProfile.classList.remove('profile-slide-out-left');
        }
        if (heroAuth) {
            heroAuth.classList.remove('auth-slide-in-right');
        }
        if (profileView) {
            profileView.style.display = 'none';
            profileView.classList.remove('profile-slide-out-down');
        }
        if (authView) {
            authView.classList.remove('auth-slide-in-up');
        }
        document.documentElement.classList.remove('profile-view-active', 'profile-reverse-active', 'profile-entering-root');
        window.history.replaceState({}, '', '/');
        showToast('info', 'Сессия успешно завершена');
    }, 650);
}
window.handleSeamlessLogout = handleSeamlessLogout;

async function loadCurrentUser() {
    try {
        const res = await fetch('/api/me');
        if (res.ok) {
            currentUser = await res.json();
            updateUserInterface(currentUser);
            if (activeServerId) {
                checkServerConsolePermissions(activeServerId);
            }
        } else if (res.status === 401) {
            if (document.querySelector('.dashboard-page')) {
                window.location.href = '/';
            }
        }
    } catch (e) {
        console.warn('Could not fetch user profile:', e);
    }
}
window.loadCurrentUser = loadCurrentUser;

function updateUserInterface(user) {
    if (!user) return;

    // Header operator button text
    const navUsername = document.getElementById('navUsername');
    if (navUsername) {
        navUsername.textContent = user.username || 'admin';
    }

    // Данные внутри левой черной капли и карточки профиля
    const profileHeroUser = document.getElementById('profileHeroUser');
    const profileHeroRole = document.getElementById('profileHeroRole');
    const profileUsernameVal = document.getElementById('profileUsernameVal');
    const profileRoleTag = document.getElementById('profileRoleTag');

    const roleName = user.is_superadmin ? 'Супер-администратор' : 'Пользователь';
    const roleShort = user.is_superadmin ? 'SuperAdmin' : 'Пользователь';

    if (profileHeroUser) profileHeroUser.textContent = user.username;
    if (profileHeroRole) profileHeroRole.textContent = roleName;
    if (profileUsernameVal) profileUsernameVal.textContent = user.username;
    if (profileRoleTag) profileRoleTag.textContent = roleShort;

    // Render Permissions Matrix
    const profilePermsGrid = document.getElementById('profilePermsGrid');
    if (profilePermsGrid) {
        renderPermissionsMatrix(user, profilePermsGrid);
    }
}

function renderPermissionsMatrix(user, container) {
    if (!container) return;
    container.innerHTML = '';

    if (user.is_superadmin) {
        container.innerHTML = `
            <div class="server-perm-badge-row">
                <span class="server-perm-name"><i class="fa-solid fa-crown text-yellow"></i> Вся сеть (All Servers)</span>
                <div class="server-perm-tags">
                    <span class="perm-pill active telemetry"><i class="fa-solid fa-check"></i> Телеметрия</span>
                    <span class="perm-pill active console-view"><i class="fa-solid fa-terminal"></i> Консоль</span>
                    <span class="perm-pill active console-cmd"><i class="fa-solid fa-bolt"></i> Команды</span>
                </div>
            </div>
        `;
        return;
    }

    const perms = user.permissions || {};
    const serverKeys = Object.keys(perms);

    if (serverKeys.length === 0) {
        container.innerHTML = `<div class="text-muted text-center py-2">Права на серверы не назначены</div>`;
        return;
    }

    serverKeys.forEach(srvKey => {
        const p = perms[srvKey];
        const row = document.createElement('div');
        row.className = 'server-perm-badge-row';
        row.innerHTML = `
            <span class="server-perm-name"><i class="fa-solid fa-server text-accent"></i> ${escapeHtml(srvKey)}</span>
            <div class="server-perm-tags">
                <span class="perm-pill ${p.telemetry ? 'active telemetry' : 'disabled'}">
                    <i class="fa-solid ${p.telemetry ? 'fa-check' : 'fa-xmark'}"></i> Телеметрия
                </span>
                <span class="perm-pill ${p.console_view ? 'active console-view' : 'disabled'}">
                    <i class="fa-solid ${p.console_view ? 'fa-terminal' : 'fa-xmark'}"></i> Консоль
                </span>
                <span class="perm-pill ${p.console_command ? 'active console-cmd' : 'disabled'}">
                    <i class="fa-solid ${p.console_command ? 'fa-bolt' : 'fa-xmark'}"></i> Команды
                </span>
            </div>
        `;
        container.appendChild(row);
    });
}

async function handleProfilePasswordSubmit(event) {
    event.preventDefault();
    const btn = document.getElementById('profileSavePassBtn');
    const oldPassInput = document.getElementById('profile_old_password');
    const newPassInput = document.getElementById('profile_new_password');

    if (!newPassInput || newPassInput.value.length < 8) {
        showToast('error', 'Пароль должен содержать минимум 8 символов');
        return;
    }

    if (btn) btn.classList.add('loading');

    try {
        const res = await fetch('/api/password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                old_password: oldPassInput ? oldPassInput.value : '',
                new_password: newPassInput.value
            })
        });

        const data = await res.json();
        if (res.ok && data.status === 'ok') {
            showToast('success', 'Пароль успешно обновлен');
            if (oldPassInput) oldPassInput.value = '';
            newPassInput.value = '';
        } else {
            showToast('error', data.message || 'Ошибка обновления пароля');
        }
    } catch (e) {
        showToast('error', 'Сетевая ошибка при смене пароля');
    } finally {
        if (btn) btn.classList.remove('loading');
    }
}
window.handleProfilePasswordSubmit = handleProfilePasswordSubmit;


window.currentUser = currentUser;
window.initProfileView = initProfileView;
window.openProfileView = openProfileView;
window.returnToDashboardFromProfile = returnToDashboardFromProfile;
window.handleSeamlessLogout = handleSeamlessLogout;
window.loadCurrentUser = loadCurrentUser;
window.updateUserInterface = updateUserInterface;
window.renderPermissionsMatrix = renderPermissionsMatrix;
window.handleProfilePasswordSubmit = handleProfilePasswordSubmit;
