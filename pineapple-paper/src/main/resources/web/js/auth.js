/* ==========================================================================
   PineApple Web Monitor - Authentication & Entrance Transitions
   ========================================================================== */

function runDashboardEntranceAnimation(dashboardPage, successMessage) {
    if (!dashboardPage) return;

    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });

    const header = dashboardPage.querySelector('.dashboard-curved-header');
    const headerInner = dashboardPage.querySelector('.dashboard-header-inner');
    if (header && headerInner) {
        const innerHeight = headerInner.offsetHeight || 240;
        const computedBottomPadding = parseFloat(getComputedStyle(header).paddingBottom) || 56;
        const totalRestingHeight = Math.round(innerHeight + computedBottomPadding);
        header.style.setProperty('--target-header-height', totalRestingHeight + 'px');
        dashboardPage.style.setProperty('--target-header-height', totalRestingHeight + 'px');
    }

    dashboardPage.classList.add('dashboard-entering');
    document.documentElement.classList.add('auth-transition-active');
    document.body.classList.add('auth-transition-active');

    void dashboardPage.offsetHeight;
    document.documentElement.classList.remove('auth-entering-root');

    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            dashboardPage.classList.add('dashboard-morphing');
        });
    });

    setTimeout(() => {
        const centerBrand = dashboardPage.querySelector('.dash-center-brand-stage');
        if (centerBrand) {
            centerBrand.style.opacity = '0';
            centerBrand.style.display = 'none';
        }

        dashboardPage.classList.remove('dashboard-entering', 'dashboard-morphing');
        document.documentElement.classList.remove('auth-transition-active', 'auth-entering-root');
        document.body.classList.remove('auth-transition-active');
        sessionStorage.removeItem('pineapple_auth_transition');
        sessionStorage.removeItem('pineapple_auth_msg');

        if (successMessage) {
            showToast('success', successMessage);
        }

        initFlippableCards();
        syncHistoryFromServer().then(() => {
            startTelemetryPolling();
        });
    }, 1700);
}

async function triggerCinematicTransition(redirectUrl, successMessage) {
    const canvaWrapper = document.getElementById('canvaWrapper');
    const targetUrl = redirectUrl || '/dashboard';

    sessionStorage.setItem('pineapple_auth_transition', 'true');
    if (successMessage) {
        sessionStorage.setItem('pineapple_auth_msg', successMessage);
    }

    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.documentElement.classList.add('auth-transition-active');
    document.body.classList.add('auth-transition-active');

    if (canvaWrapper) {
        canvaWrapper.classList.add('auth-transitioning');
    }

    setTimeout(() => {
        window.location.href = targetUrl;
    }, 1050);
}

async function handleLoginSubmit(event) {
    event.preventDefault();

    const form = event.target;
    const submitBtn = document.getElementById('loginSubmitBtn');
    if (submitBtn && submitBtn.classList.contains('loading')) return;

    const formData = new FormData(form);
    const passInput = document.getElementById('login_pass');
    const rememberMeBox = document.getElementById('remember_me');

    const payload = {
        login: formData.get('login') || '',
        password: formData.get('password') || '',
        remember_me: rememberMeBox ? rememberMeBox.checked : true
    };

    if (submitBtn) {
        submitBtn.classList.add('loading');
        const icon = submitBtn.querySelector('.btn-icon i');
        if (icon) icon.className = 'fa-solid fa-spinner fa-spin';
    }

    try {
        const response = await fetch(form.action || '/login', {
            method: 'POST',
            body: JSON.stringify(payload),
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });

        const data = await response.json();

        if (response.ok && data.success) {
            if (rememberMeBox && rememberMeBox.checked) {
                localStorage.setItem('pineapple_saved_user', payload.login);
                localStorage.setItem('pineapple_remember_me', 'true');
            } else {
                localStorage.removeItem('pineapple_saved_user');
                localStorage.setItem('pineapple_remember_me', 'false');
            }
            await triggerCinematicTransition(data.redirect || '/dashboard', data.message);
        } else {
            showToast('error', data.message || t('toast_invalid_creds', 'Invalid credentials. Check config.yml'));
            triggerFormError(form, passInput);
            resetSubmitButton(submitBtn, 'fa-arrow-right');
        }
    } catch (err) {
        console.error('Login error:', err);
        form.submit();
    }
}

async function handleLogout(event) {
    if (event) event.preventDefault();
    stopTelemetryPolling();
    try {
        await fetch('/logout', { method: 'POST' });
    } catch (e) {}
    window.location.href = '/';
}


window.runDashboardEntranceAnimation = runDashboardEntranceAnimation;
window.triggerCinematicTransition = triggerCinematicTransition;
window.handleLoginSubmit = handleLoginSubmit;
window.handleLogout = handleLogout;
