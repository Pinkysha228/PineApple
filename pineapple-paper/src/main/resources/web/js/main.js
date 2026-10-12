/* ==========================================================================
   PineApple Web Monitor - Core Application Bootstrap
   Modularized architecture for simplified maintenance.
   ========================================================================== */

// Fallback loader: dynamically ensure modular scripts are loaded if main.js is loaded standalone
(function ensureModularDependencies() {
    const modules = [
        { ready: () => typeof window.TRANSLATIONS !== 'undefined', src: '/static/js/i18n.js' },
        { ready: () => typeof window.showToast !== 'undefined', src: '/static/js/utils.js' },
        { ready: () => typeof window.handleLoginSubmit !== 'undefined', src: '/static/js/auth.js' },
        { ready: () => typeof window.loadCurrentUser !== 'undefined', src: '/static/js/profile.js' },
        { ready: () => typeof window.sendConsoleCommand !== 'undefined', src: '/static/js/console.js' },
        { ready: () => typeof window.startTelemetryPolling !== 'undefined', src: '/static/js/dashboard.js' }
    ];
    const missing = modules.filter(m => !m.ready());
    if (missing.length > 0 && typeof document !== 'undefined') {
        missing.forEach(m => {
            const s = document.createElement('script');
            s.src = m.src;
            s.async = false;
            document.head.appendChild(s);
        });
    }
})();

window.addEventListener('popstate', () => {
    stopTelemetryPolling();
    window.location.reload();
});


document.addEventListener('DOMContentLoaded', () => {
    const dashboardPage = document.querySelector('.dashboard-page');
    if (dashboardPage && sessionStorage.getItem('pineapple_auth_transition') === 'true') {
        const successMsg = sessionStorage.getItem('pineapple_auth_msg');
        runDashboardEntranceAnimation(dashboardPage, successMsg);
        loadCurrentUser();
    } else if (dashboardPage) {
        const centerBrand = dashboardPage.querySelector('.dash-center-brand-stage');
        if (centerBrand) {
            centerBrand.style.opacity = '0';
            centerBrand.style.display = 'none';
        }
        initFlippableCards();
        syncHistoryFromServer().then(() => {
            startTelemetryPolling();
        });
        loadCurrentUser();
    }

    const canvaWrapper = document.getElementById('canvaWrapper');
    const isProfilePage = window.location.pathname === '/profile' || 
                          window.location.search.includes('view=profile') || 
                          sessionStorage.getItem('pineapple_reverse_profile') === 'true' ||
                          document.documentElement.classList.contains('profile-view-active');

    if (canvaWrapper && isProfilePage) {
        initProfileView();
    }

    initLanguageDropdowns();
    applyTheme(getStoredTheme());
    applyLanguage(getLanguage());

    const savedUser = localStorage.getItem('pineapple_saved_user');
    const rememberPref = localStorage.getItem('pineapple_remember_me');
    const userInput = document.getElementById('login_user');
    const rememberBox = document.getElementById('remember_me');
    if (userInput && savedUser) {
        userInput.value = savedUser;
    }
    if (rememberBox && rememberPref !== null) {
        rememberBox.checked = rememberPref === 'true';
    }

    document.querySelectorAll('.toast').forEach(toast => {
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(40px)';
            setTimeout(() => toast.remove(), 320);
        }, 4500);
    });

    document.addEventListener('click', (e) => {
        const button = e.target.closest('.ripple-effect');
        if (!button) return;

        const circle = document.createElement('span');
        const diameter = Math.max(button.clientWidth, button.clientHeight);
        const radius = diameter / 2;

        const rect = button.getBoundingClientRect();
        circle.style.width = circle.style.height = `${diameter}px`;
        circle.style.left = `${e.clientX - rect.left - radius}px`;
        circle.style.top = `${e.clientY - rect.top - radius}px`;
        circle.classList.add('ripple-circle');

        const existingRipple = button.querySelector('.ripple-circle');
        if (existingRipple) {
            existingRipple.remove();
        }

        button.appendChild(circle);

        setTimeout(() => {
            circle.remove();
        }, 600);
    });
});


