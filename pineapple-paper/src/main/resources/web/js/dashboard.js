/* ==========================================================================
   PineApple Web Monitor - Telemetry Polling, Multi-Server Hub & Charts
   ========================================================================== */

let currentView = "hub";
let activeServerId = null;
let activeServerName = null;
let isCurtainTransitioning = false;
let isTelemetryPaused = false;
let telemetryTimer = null;
let historySyncTimer = null;
let metricHistory = [];
const activeChartRanges = {
    tps: "1m",
    ram: "1m",
    cpu: "1m"
};

function getTelemetryInterval() {
    return window.PINEAPPLE_REFRESH_MS || 2500;
}

function startTelemetryPolling() {
    if (telemetryTimer) clearInterval(telemetryTimer);
    if (historySyncTimer) clearInterval(historySyncTimer);
    const dashboardPage = document.querySelector(".dashboard-page");
    if (!dashboardPage) return;

    if (currentView === "hub") {
        void fetchNetworkServers();
    } else {
        void fetchLiveStats();
    }

    telemetryTimer = setInterval(() => {
        if (!isTelemetryPaused) {
            if (currentView === "hub") {
                void fetchNetworkServers();
            } else {
                void fetchLiveStats();
            }
        }
    }, getTelemetryInterval());

    historySyncTimer = setInterval(() => {
        if (!isTelemetryPaused && currentView === "server") {
            void syncHistoryFromServer();
        }
    }, 20000);
}

function stopTelemetryPolling() {
    if (telemetryTimer) {
        clearInterval(telemetryTimer);
        telemetryTimer = null;
    }
    if (historySyncTimer) {
        clearInterval(historySyncTimer);
        historySyncTimer = null;
    }
}

function toggleLiveTelemetry() {
    isTelemetryPaused = !isTelemetryPaused;
    const btnHub = document.getElementById("btnToggleLiveHub");
    const btnSrv = document.getElementById("btnToggleLiveServer");
    const iconHub = document.getElementById("liveIconHub");
    const iconSrv = document.getElementById("liveIconServer");
    const textHub = document.getElementById("liveBtnTextHub");
    const textSrv = document.getElementById("liveBtnTextServer");
    const pill = document.getElementById("liveStatusPill");

    if (isTelemetryPaused) {
        if (iconHub) iconHub.className = "fa-solid fa-play";
        if (iconSrv) iconSrv.className = "fa-solid fa-play";
        if (textHub) textHub.textContent = t("btn_resume", "Resume Stream");
        if (textSrv) textSrv.textContent = t("btn_resume", "Resume Stream");
        if (pill) pill.classList.add("paused");
        showToast("info", t("toast_polling_paused", "Telemetry stream paused"), 2000);
    } else {
        if (iconHub) iconHub.className = "fa-solid fa-pause";
        if (iconSrv) iconSrv.className = "fa-solid fa-pause";
        if (textHub) textHub.textContent = t("btn_pause", "Pause Stream");
        if (textSrv) textSrv.textContent = t("btn_pause", "Pause Stream");
        if (pill) pill.classList.remove("paused");
        showToast("success", t("toast_polling_resumed", "Stream resumed"), 2000);
        if (currentView === "hub") {
            void fetchNetworkServers(true);
        } else {
            void fetchLiveStats(true);
            void syncHistoryFromServer();
        }
    }
}

function refreshCurrentView(isManual = false) {
    if (currentView === "hub") {
        fetchNetworkServers(isManual);
    } else {
        fetchLiveStats(isManual);
        syncHistoryFromServer();
    }
}

async function fetchNetworkServers(isManual = false) {
    const listContainer = document.getElementById("networkServersList");
    if (!listContainer && currentView !== "hub") return;

    try {
        const response = await fetch("/api/servers", {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (response.status === 401) {
            stopTelemetryPolling();
            window.location.href = "/";
            return;
        }
        if (!response.ok) return;

        const data = await response.json();
        const activeServersEl = document.getElementById("hubActiveServers");
        const totalPlayersEl = document.getElementById("hubTotalPlayers");
        const lastUpdatedEl = document.getElementById("valHubLastUpdated");

        if (activeServersEl) {
            activeServersEl.textContent = `${data.active_count || 0} / ${data.total_count || 0}`;
        }
        if (totalPlayersEl) {
            totalPlayersEl.textContent = `${data.total_players || 0}`;
        }
        if (lastUpdatedEl) {
            const now = new Date();
            const timeStr = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
            lastUpdatedEl.textContent = `Synced: ${timeStr}`;
        }

        renderNetworkServersList(data.servers || []);
    } catch (e) {
        console.warn("Failed to fetch network servers overview:", e);
    }
}

function renderNetworkServersList(servers) {
    const listContainer = document.getElementById("networkServersList");
    if (!listContainer) return;

    if (!servers || servers.length === 0) {
        listContainer.innerHTML = `
            <div class="hub-empty-state text-center text-muted py-5">
                <i class="fa-solid fa-server fa-2x mb-3 text-accent"></i>
                <p>No backend servers discovered yet. Make sure PineApple is installed on your Paper servers.</p>
            </div>
        `;
        return;
    }

    listContainer.innerHTML = servers.map(srv => {
        const serverId = srv.server_id || srv.id || "";
        const serverName = srv.server_name || srv.name || serverId || "Server";
        const isOnline = Boolean(srv.online);
        const tpsVal = typeof srv.tps === "number" ? srv.tps : 20.0;
        const tpsPct = Math.min(100, Math.max(0, (tpsVal / 20.0) * 100));

        let ramUsedVal = srv.ram_used_gb;
        if (typeof ramUsedVal !== "number" && typeof srv.ram_used_mb === "number") {
            ramUsedVal = srv.ram_used_mb / 1024.0;
        }
        const ramUsed = typeof ramUsedVal === "number" ? ramUsedVal.toFixed(1) : "0.0";

        let rawRamPct = 0;
        if (typeof srv.ram_percent === "number" && srv.ram_percent > 0) {
            rawRamPct = srv.ram_percent;
        } else if (typeof srv.ram_pct === "number" && srv.ram_pct > 0) {
            rawRamPct = srv.ram_pct;
        } else if (typeof srv.ram_used_mb === "number" && typeof srv.ram_max_mb === "number" && srv.ram_max_mb > 0) {
            rawRamPct = (srv.ram_used_mb / srv.ram_max_mb) * 100;
        } else if (typeof srv.ram_used_gb === "number" && typeof srv.ram_max_gb === "number" && srv.ram_max_gb > 0) {
            rawRamPct = (srv.ram_used_gb / srv.ram_max_gb) * 100;
        }
        const ramPct = Math.min(100, Math.max(0, rawRamPct));
        const cpuPct = Math.min(100, Math.max(0, typeof srv.cpu_percent === "number" ? srv.cpu_percent : (srv.cpu_pct || 0)));

        const tpsState = getMetricState('tps', tpsVal);
        const tpsTheme = getStateTheme(tpsState, '#10b981');

        const ramState = getMetricState('ram', ramPct);
        const ramTheme = getStateTheme(ramState, '#3b82f6');

        const cpuState = getMetricState('cpu', cpuPct);
        const cpuTheme = getStateTheme(cpuState, '#8b5cf6');

        const safeId = escapeHtml(serverId);
        const safeName = escapeHtml(serverName);
        const safeVer = escapeHtml(srv.version || "Paper 1.21.4");

        return `
            <div class="server-row-card" data-server-id="${safeId}" data-server-name="${safeName}" onclick="openServerDetails(this.dataset.serverId, this.dataset.serverName)">
                <div class="server-row-identity">
                    <div class="server-status-dot ${isOnline ? "online" : "offline"}"></div>
                    <div class="server-name-wrap">
                        <h3 class="server-row-name">${safeName}</h3>
                        <span class="server-row-version">${safeVer}</span>
                    </div>
                </div>

                <!-- Metric: TPS -->
                <div class="server-row-metric">
                    <div class="server-metric-text">
                        <span class="server-metric-val font-mono ${tpsTheme.textClass}">${tpsVal.toFixed(1)}</span>
                        <span class="server-metric-unit">TPS</span>
                    </div>
                    <div class="meter-bar-track meter-bar-mini">
                        <div class="meter-bar-fill ${tpsTheme.fillClass} ${tpsPct > 0 ? 'has-val' : ''}" style="width: ${tpsPct}%;"></div>
                    </div>
                </div>

                <!-- Metric: RAM -->
                <div class="server-row-metric">
                    <div class="server-metric-text">
                        <span class="server-metric-val font-mono ${ramTheme.textClass}">${ramUsed} GB</span>
                        <span class="server-metric-unit">RAM</span>
                    </div>
                    <div class="meter-bar-track meter-bar-mini">
                        <div class="meter-bar-fill ${ramTheme.fillClass} ${ramPct > 0 ? 'has-val' : ''}" style="width: ${ramPct}%;"></div>
                    </div>
                </div>

                <!-- Metric: CPU -->
                <div class="server-row-metric">
                    <div class="server-metric-text">
                        <span class="server-metric-val font-mono ${cpuTheme.textClass}">${Math.round(cpuPct)}%</span>
                        <span class="server-metric-unit">CPU</span>
                    </div>
                    <div class="meter-bar-track meter-bar-mini">
                        <div class="meter-bar-fill ${cpuTheme.fillClass} ${cpuPct > 0 ? 'has-val' : ''}" style="width: ${cpuPct}%;"></div>
                    </div>
                </div>

                <!-- Players & Arrow -->
                <div class="server-row-action">
                    <div class="server-players-badge">
                        <i class="fa-solid fa-users"></i>
                        <span class="font-mono">${srv.players_count || 0} / ${srv.max_players || 20}</span>
                    </div>
                    <div class="server-row-arrow">
                        <i class="fa-solid fa-chevron-right"></i>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

function runCurtainTransition(destinationView, destinationTitle, destinationSubtitle, onSwapCallback) {
    if (isCurtainTransitioning) return;
    isCurtainTransitioning = true;

    const curtain = document.getElementById("cinematicCurtain");
    const curtainTitle = document.getElementById("cinematicCurtainTitle");
    const curtainSub = document.getElementById("cinematicCurtainSub");
    const navTitle = document.getElementById("navBrandArea");
    const navServerName = document.getElementById("navServerNameText");
    const navSubtitle = document.getElementById("navSubtitleTag");

    if (curtainTitle) {
        curtainTitle.textContent = destinationTitle || "PineApple";
    }
    if (curtainSub) {
        curtainSub.textContent = destinationSubtitle || (destinationView === "hub" ? "Network Overview" : "Server Telemetry");
    }

    if (!curtain) {
        if (onSwapCallback) onSwapCallback();
        if (navServerName) navServerName.textContent = "PineApple";
        isCurtainTransitioning = false;
        return;
    }

    const page = document.getElementById("dashboardPage") || document.querySelector(".dashboard-page");

    curtain.classList.remove("curtain-retracting");
    curtain.classList.add("curtain-dropping");
    if (page) {
        page.classList.remove("curtain-retracting");
        page.classList.add("curtain-dropping");
    }

    setTimeout(() => {
        if (onSwapCallback) {
            onSwapCallback();
        }
        if (navServerName) {
            navServerName.textContent = destinationTitle || "PineApple";
        }
        if (navSubtitle) {
            navSubtitle.textContent = destinationSubtitle || (destinationView === "hub" ? "NETWORK MONITOR" : "SERVER MONITOR");
        }
        window.scrollTo(0, 0);

        curtain.classList.remove("curtain-dropping");
        curtain.classList.add("curtain-retracting");
        if (page) {
            page.classList.remove("curtain-dropping");
            page.classList.add("curtain-retracting");
        }

        if (navTitle) {
            navTitle.classList.remove("nav-brand-slide-in");
            void navTitle.offsetWidth;
            navTitle.classList.add("nav-brand-slide-in");
        }

        setTimeout(() => {
            curtain.classList.remove("curtain-retracting");
            if (page) {
                page.classList.remove("curtain-retracting");
            }
            if (navTitle) {
                navTitle.classList.remove("nav-brand-slide-in");
            }
            isCurtainTransitioning = false;
        }, 850);
    }, 800);
}

function openServerDetails(serverId, serverName) {
    const displayName = serverName || serverId;
    runCurtainTransition("server", displayName, displayName.toUpperCase() + " • MONITOR", () => {
        switchToServerView(serverId, displayName);
    });
}

function transitionToServer(serverId, serverName) {
    openServerDetails(serverId, serverName);
}

function switchToNetworkHub() {
    runCurtainTransition("hub", "PineApple", "NETWORK MONITOR", () => {
        switchToHubView();
    });
}

function switchToServerView(serverId, displayName) {
    currentView = "server";
    activeServerId = serverId;
    activeServerName = displayName;

    const hubView = document.getElementById("networkHubView");
    const srvView = document.getElementById("serverDashboardView");
    const hubBanner = document.getElementById("hubHeroBanner");
    const srvBanner = document.getElementById("serverHeroBanner");
    const btnReturn = document.getElementById("btnReturnToHub");
    const navServerName = document.getElementById("navServerNameText");
    const navSubtitle = document.getElementById("navSubtitleTag");

    if (hubView) hubView.style.display = "none";
    if (srvView) srvView.style.display = "block";
    if (hubBanner) hubBanner.style.display = "none";
    if (srvBanner) srvBanner.style.display = "flex";
    if (btnReturn) btnReturn.style.display = "inline-flex";
    if (navServerName) navServerName.textContent = displayName || "PineApple";
    if (navSubtitle) navSubtitle.textContent = (displayName || serverId).toUpperCase() + " • MONITOR";

    metricHistory = [];
    initFlippableCards();
    syncHistoryFromServer().then(() => {
        fetchLiveStats();
    });
    checkServerConsolePermissions(serverId);
}

function switchToHubView() {
    currentView = "hub";
    activeServerId = null;
    activeServerName = null;

    const hubView = document.getElementById("networkHubView");
    const srvView = document.getElementById("serverDashboardView");
    const hubBanner = document.getElementById("hubHeroBanner");
    const srvBanner = document.getElementById("serverHeroBanner");
    const btnReturn = document.getElementById("btnReturnToHub");
    const navServerName = document.getElementById("navServerNameText");
    const navSubtitle = document.getElementById("navSubtitleTag");

    if (hubView) hubView.style.display = "flex";
    if (srvView) srvView.style.display = "none";
    if (hubBanner) hubBanner.style.display = "flex";
    if (srvBanner) srvBanner.style.display = "none";
    if (btnReturn) btnReturn.style.display = "none";
    if (navServerName) navServerName.textContent = "PineApple";
    if (navSubtitle) navSubtitle.textContent = "NETWORK MONITOR";

    stopConsolePolling();
    fetchNetworkServers();
}

async function fetchLiveStats(isManual = false) {
    if (currentView !== "server" || !activeServerId) return;

    try {
        const url = `/api/stats?server=${encodeURIComponent(activeServerId)}`;
        const response = await fetch(url, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });

        if (response.status === 401) {
            stopTelemetryPolling();
            window.location.href = "/";
            return;
        }

        if (!response.ok) return;

        const data = await response.json();
        updateDashboardUI(data);

        const now = Date.now();
        const tpsVal = data.tps ? (data.tps.current !== undefined ? data.tps.current : 20.0) : 20.0;
        const ramVal = data.memory ? (data.memory.used_mb !== undefined ? data.memory.used_mb : (data.memory.heap_used_mb || 0)) : 0;
        const ramMax = data.memory ? (data.memory.max_mb !== undefined ? data.memory.max_mb : (data.memory.heap_max_mb || 1024)) : 1024;
        const ramPct = data.memory ? (data.memory.percentage !== undefined ? data.memory.percentage : (data.memory.heap_percent !== undefined ? data.memory.heap_percent : 0)) : 0;
        const cpuPct = data.cpu ? (data.cpu.process_percentage !== undefined ? data.cpu.process_percentage : (data.cpu.process_percent || 0)) : 0;

        metricHistory.push({
            timestamp: now,
            tps: tpsVal,
            ramUsedMb: ramVal,
            ramMaxMb: ramMax,
            ramPct: ramPct,
            cpuPct: cpuPct
        });

        if (metricHistory.length > 360) {
            metricHistory.shift();
        }

        ["tps", "ram", "cpu"].forEach(metric => {
            const card = document.getElementById(`card${metric.charAt(0).toUpperCase() + metric.slice(1)}`);
            if (card && card.classList.contains("flipped")) {
                renderMetricChart(metric);
            }
        });
    } catch (e) {
        console.warn("Failed to fetch live stats for server:", e);
    }
}

function updateDashboardUI(data) {
    if (!data) return;

    const srvVer = document.getElementById("valServerVersion");
    const javaVer = document.getElementById("valJavaVersion");
    const playersCount = document.getElementById("valPlayersCount");
    const uptime = document.getElementById("valUptime");
    const lastUpdated = document.getElementById("valLastUpdated");

    if (srvVer) srvVer.textContent = data.version || (data.server && data.server.version) || "Paper 1.21.4";
    if (javaVer) javaVer.textContent = data.java_version || (data.server && data.server.java_version) || "Java 21";
    if (uptime) uptime.textContent = data.uptime || (data.system && data.system.uptime) || "0s";
    if (playersCount && data.players) {
        playersCount.textContent = `${data.players.online || 0} / ${data.players.max || 20}`;
    }
    if (lastUpdated) {
        const now = new Date();
        lastUpdated.textContent = `Synced: ${now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}`;
    }

    if (data.tps) {
        const val = data.tps.current !== undefined ? data.tps.current : 20.0;
        const pct = Math.min(100, Math.max(0, (val / 20.0) * 100));
        const valTps = document.getElementById("valTps");
        const barTps = document.getElementById("barTps");
        const valMspt = document.getElementById("valMspt");
        const tps1m = document.getElementById("valTps1m");
        const tps5m = document.getElementById("valTps5m");
        const tps15m = document.getElementById("valTps15m");

        if (valTps) {
            valTps.textContent = val.toFixed(1);
            valTps.className = `metric-value-huge ${val >= 19.0 ? 'text-green' : (val >= 15.0 ? 'text-yellow' : 'text-red')}`;
        }
        if (barTps) {
            barTps.style.width = `${pct}%`;
            barTps.className = `meter-bar-fill ${val >= 19.0 ? 'fill-green' : (val >= 15.0 ? 'fill-warning' : 'fill-danger')} ${pct > 0 ? 'has-val' : ''}`;
        }
        const mspt = data.tps.mspt || 0;
        if (valMspt) valMspt.textContent = `MSPT: ${mspt.toFixed(1)} ms / 50.0 ms`;
        const m1 = (data.tps["1m"] !== undefined ? data.tps["1m"] : (data.tps.history ? data.tps.history[0] : val)).toFixed(1);
        const m5 = (data.tps["5m"] !== undefined ? data.tps["5m"] : (data.tps.history ? data.tps.history[1] : val)).toFixed(1);
        const m15 = (data.tps["15m"] !== undefined ? data.tps["15m"] : (data.tps.history ? data.tps.history[2] : val)).toFixed(1);
        if (tps1m) tps1m.textContent = m1;
        if (tps5m) tps5m.textContent = m5;
        if (tps15m) tps15m.textContent = m15;
        const valTpsBreakdown = document.getElementById("valTpsBreakdown");
        if (valTpsBreakdown) valTpsBreakdown.textContent = `${m1}, ${m5}, ${m15}`;
        const valTickHealth = document.getElementById("valTickHealth");
        if (valTickHealth) {
            if (mspt < 40) {
                valTickHealth.textContent = `Optimal (${mspt.toFixed(1)} ms)`;
                valTickHealth.className = "info-val font-mono nowrap text-green";
            } else if (mspt < 50) {
                valTickHealth.textContent = `Normal (${mspt.toFixed(1)} ms)`;
                valTickHealth.className = "info-val font-mono nowrap text-yellow";
            } else {
                valTickHealth.textContent = `Lagging (${mspt.toFixed(1)} ms)`;
                valTickHealth.className = "info-val font-mono nowrap text-red";
            }
        }
    }

    if (data.memory) {
        const ramUsed = data.memory.used_mb !== undefined ? data.memory.used_mb : (data.memory.heap_used_mb || 0);
        const ramMax = data.memory.max_mb !== undefined ? data.memory.max_mb : (data.memory.heap_max_mb || 1024);
        const ramPct = data.memory.percentage !== undefined ? data.memory.percentage : (data.memory.heap_percent !== undefined ? data.memory.heap_percent : (ramMax > 0 ? (ramUsed / ramMax) * 100 : 0));
        const ramCommitted = data.memory.committed_mb !== undefined ? data.memory.committed_mb : (data.memory.heap_committed_mb || 0);

        const valRamPct = document.getElementById("valRamPercent");
        const barRamPct = document.getElementById("barRamPercent");
        const valRamUsed = document.getElementById("valRamUsedTotal");
        const valCommitted = document.getElementById("valHeapCommitted");
        const valSysRam = document.getElementById("valSystemRam");

        const clampedRamPct = Math.min(100, Math.max(0, ramPct));
        if (valRamPct) {
            valRamPct.textContent = `${Math.round(ramPct)}%`;
            valRamPct.className = `metric-value-huge ${ramPct > 90 ? 'text-red' : (ramPct > 75 ? 'text-yellow' : 'text-blue')}`;
        }
        if (barRamPct) {
            barRamPct.style.width = `${clampedRamPct}%`;
            barRamPct.className = `meter-bar-fill ${ramPct > 90 ? 'fill-danger' : (ramPct > 75 ? 'fill-warning' : 'fill-blue')} ${clampedRamPct > 0 ? 'has-val' : ''}`;
        }
        if (valRamUsed) valRamUsed.textContent = `${ramUsed} MB / ${ramMax} MB`;
        if (valCommitted) valCommitted.textContent = `${ramCommitted} MB`;
        if (valSysRam) {
            const sysGb = ((data.memory.system_total_mb || 0) / 1024).toFixed(1);
            valSysRam.textContent = `${sysGb} GB Total`;
        }
    }

    if (data.cpu) {
        const procPct = data.cpu.process_percentage !== undefined ? data.cpu.process_percentage : (data.cpu.process_percent || 0);
        const sysPct = data.cpu.system_percentage !== undefined ? data.cpu.system_percentage : (data.cpu.system_percent || 0);
        const cores = data.cpu.cores !== undefined ? data.cpu.cores : (data.cpu.available_processors || 1);

        const valCpu = document.getElementById("valCpuPercent");
        const barCpu = document.getElementById("barCpuPercent");
        const valHost = document.getElementById("valCpuHost");
        const valCores = document.getElementById("valCpuCores");

        const clampedCpuPct = Math.min(100, Math.max(0, procPct));
        if (valCpu) {
            valCpu.textContent = `${Math.round(procPct)}%`;
            valCpu.className = `metric-value-huge ${procPct > 90 ? 'text-red' : (procPct > 75 ? 'text-yellow' : 'text-purple')}`;
        }
        if (barCpu) {
            barCpu.style.width = `${clampedCpuPct}%`;
            barCpu.className = `meter-bar-fill ${procPct > 90 ? 'fill-danger' : (procPct > 75 ? 'fill-warning' : 'fill-purple')} ${clampedCpuPct > 0 ? 'has-val' : ''}`;
        }
        const valCpuProc = document.getElementById("valCpuProcess");
        const valCpuSys = document.getElementById("valCpuSystem");
        if (valCpuProc) valCpuProc.textContent = `${procPct.toFixed(1)}%`;
        if (valCpuSys) valCpuSys.textContent = `${sysPct.toFixed(1)}%`;
        if (valHost) valHost.textContent = `${sysPct.toFixed(1)}%`;
        if (valCores) valCores.textContent = `Host: ${sysPct.toFixed(0)}% (${cores} Cores)`;
    }

    const pTableBody = document.getElementById("playersTableBody");
    if (pTableBody && data.players) {
        const list = data.players.list || [];
        if (list.length === 0) {
            pTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-3" data-i18n="no_players_online">No players currently connected</td></tr>`;
        } else {
            pTableBody.innerHTML = list.map(p => `
                <tr>
                    <td style="width: 54px;">
                        <img src="https://mc-heads.net/avatar/${escapeHtml(p.name)}/28" alt="${escapeHtml(p.name)}" class="player-avatar" loading="lazy" onerror="this.outerHTML='<span class="avatar-fallback-icon"><i class="fa-solid fa-user"></i></span>'">
                    </td>
                    <td><strong>${escapeHtml(p.name)}</strong></td>
                    <td>${escapeHtml(p.world || "world")}</td>
                    <td><span class="badge-pill font-mono">${escapeHtml(p.gamemode || "SURVIVAL")}</span></td>
                    <td style="text-align: right;" class="font-mono ${p.ping < 100 ? "text-emerald-500" : "text-amber-500"}">${p.ping || 0} ms</td>
                </tr>
            `).join("");
        }
    }

    const valTotalChunks = document.getElementById("valTotalChunks");
    const valTotalEntities = document.getElementById("valTotalEntities");
    const wTableBody = document.getElementById("worldsTableBody");

    let worldsList = [];
    let totalChunks = 0;
    let totalEntities = 0;

    if (data.worlds) {
        if (Array.isArray(data.worlds)) {
            worldsList = data.worlds;
            worldsList.forEach(w => {
                totalChunks += (w.loaded_chunks !== undefined ? w.loaded_chunks : (w.chunks || 0));
                totalEntities += (w.entities || 0);
            });
        } else if (typeof data.worlds === "object") {
            totalChunks = data.worlds.total_chunks || 0;
            totalEntities = data.worlds.total_entities || 0;
            worldsList = data.worlds.list || data.worlds.worlds || [];
        }
    }

    if (valTotalChunks) valTotalChunks.textContent = totalChunks;
    if (valTotalEntities) valTotalEntities.textContent = totalEntities;

    if (wTableBody) {
        if (worldsList.length === 0) {
            wTableBody.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-3">No world data reported</td></tr>`;
        } else {
            wTableBody.innerHTML = worldsList.map(w => `
                <tr>
                    <td><strong>${escapeHtml(w.name)}</strong></td>
                    <td><span class="badge-pill">${escapeHtml(w.environment || "NORMAL")}</span></td>
                    <td class="font-mono text-right">${w.loaded_chunks !== undefined ? w.loaded_chunks : (w.chunks || 0)}</td>
                    <td class="font-mono text-right">${w.entities || 0}</td>
                </tr>
            `).join("");
        }
    }
}

async function syncHistoryFromServer() {
    const srvParam = activeServerId ? `?server=${encodeURIComponent(activeServerId)}` : '';
    try {
        const res = await fetch(`/api/history${srvParam}`, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (!res.ok) return;

        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
            metricHistory = data.map(item => {
                let ts = item.timestamp || item.t || Date.now();
                if (ts < 10000000000) ts = ts * 1000;
                return {
                    timestamp: ts,
                    tps: item.tps !== undefined ? item.tps : 20.0,
                    ramUsedMb: item.ram_used_mb !== undefined ? item.ram_used_mb : (item.ramUsedMb !== undefined ? item.ramUsedMb : (item.ram || 0)),
                    ramMaxMb: item.ram_max_mb !== undefined ? item.ram_max_mb : (item.ramMaxMb !== undefined ? item.ramMaxMb : (item.ram_max || 1024)),
                    ramPct: item.ram_pct !== undefined ? item.ram_pct : (item.ramPercent !== undefined ? item.ramPercent : 0),
                    cpuPct: item.cpu_pct !== undefined ? item.cpu_pct : (item.cpuProcessPct !== undefined ? item.cpuProcessPct : (item.cpu || 0))
                };
            });

            ["tps", "ram", "cpu"].forEach(metric => {
                const card = document.getElementById(`card${metric.charAt(0).toUpperCase() + metric.slice(1)}`);
                if (card && card.classList.contains("flipped")) {
                    renderMetricChart(metric);
                }
            });
        }
    } catch (e) {
        console.warn("Failed to sync history buffer:", e);
    }
}

function renderMetricChart(metric) {
    const cap = metric.charAt(0).toUpperCase() + metric.slice(1);
    const canvas = document.getElementById(`chartCanvas${cap}`) || document.getElementById(`chart${cap}`);
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    const parent = canvas.parentElement;

    let W = rect.width;
    let H = rect.height;
    if (!W || W < 10) {
        W = parent ? parent.clientWidth : 300;
    }
    if (!H || H < 10) {
        H = parent ? parent.clientHeight : 145;
    }
    if (!W || !H) return;

    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.resetTransform();
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, W, H);

    const range = activeChartRanges[metric] || "1m";
    const rangeMs = range === "1h" ? 3600000 : (range === "30m" ? 1800000 : 60000);
    const now = Date.now();
    const cutoff = now - rangeMs;

    let points = metricHistory.filter(pt => pt.timestamp >= cutoff);
    if (points.length === 0 && metricHistory.length > 0) {
        points = metricHistory.slice(-60);
    }

    let yMin = 0;
    let yMax = 20;
    let yUnit = "TPS";
    let lineColor = "#10b981";
    let fillColor = "rgba(16, 185, 129, 0.12)";

    if (metric === "tps") {
        yMin = 0;
        yMax = 20;
        yUnit = "TPS";
        lineColor = "#10b981";
        fillColor = "rgba(16, 185, 129, 0.12)";
    } else if (metric === "ram") {
        yMin = 0;
        const maxVal = Math.max(...(points.length > 0 ? points.map(p => p.ramUsedMb) : [512]), 512);
        yMax = Math.ceil(maxVal * 1.15);
        yUnit = "MB";
        lineColor = "#3b82f6";
        fillColor = "rgba(59, 130, 246, 0.12)";
    } else if (metric === "cpu") {
        yMin = 0;
        yMax = 100;
        yUnit = "%";
        lineColor = "#8b5cf6";
        fillColor = "rgba(139, 92, 246, 0.14)";
    }

    const padding = { top: 20, right: 16, bottom: 24, left: 42 };
    const plotW = Math.max(1, W - padding.left - padding.right);
    const plotH = Math.max(1, H - padding.top - padding.bottom);

    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    ctx.strokeStyle = isDark ? "rgba(255, 255, 255, 0.08)" : "#e2e8f0";
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let i = 0; i <= 4; i++) {
        const y = padding.top + (plotH / 4) * i;
        ctx.moveTo(padding.left, y);
        ctx.lineTo(W - padding.right, y);

        const val = yMax - ((yMax - yMin) / 4) * i;
        ctx.fillStyle = isDark ? "#64748b" : "#94a3b8";
        ctx.font = "10px ui-monospace, SFMono-Regular, monospace";
        ctx.textAlign = "right";
        ctx.textBaseline = "middle";
        ctx.fillText(`${Math.round(val)} ${i === 0 ? yUnit : ""}`, padding.left - 6, y);
    }
    ctx.stroke();

    const minElem = document.getElementById(`chartMin${cap}`);
    const avgElem = document.getElementById(`chartAvg${cap}`);
    const maxElem = document.getElementById(`chartMax${cap}`);

    if (points.length === 0) {
        ctx.fillStyle = isDark ? "#64748b" : "#94a3b8";
        ctx.font = "12px system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText("Waiting for telemetry stream...", W / 2, padding.top + plotH / 2);

        if (minElem) minElem.textContent = metric === "ram" ? "0 MB" : (metric === "cpu" ? "0.0%" : "0.0");
        if (avgElem) avgElem.textContent = metric === "ram" ? "0 MB" : (metric === "cpu" ? "0.0%" : "0.0");
        if (maxElem) maxElem.textContent = metric === "ram" ? "0 MB" : (metric === "cpu" ? "0.0%" : "0.0");
        return;
    }

    let renderPoints = points.slice();
    if (renderPoints.length === 1) {
        renderPoints.unshift({
            timestamp: cutoff,
            tps: renderPoints[0].tps,
            ramUsedMb: renderPoints[0].ramUsedMb,
            ramMaxMb: renderPoints[0].ramMaxMb,
            ramPct: renderPoints[0].ramPct,
            cpuPct: renderPoints[0].cpuPct
        });
    }

    const vals = points.map(pt => metric === "tps" ? pt.tps : (metric === "ram" ? pt.ramUsedMb : pt.cpuPct));
    const minV = Math.min(...vals);
    const maxV = Math.max(...vals);
    const avgV = vals.reduce((a, b) => a + b, 0) / vals.length;

    if (minElem) minElem.textContent = metric === "ram" ? `${Math.round(minV)} MB` : (metric === "cpu" ? `${minV.toFixed(1)}%` : minV.toFixed(1));
    if (avgElem) avgElem.textContent = metric === "ram" ? `${Math.round(avgV)} MB` : (metric === "cpu" ? `${avgV.toFixed(1)}%` : avgV.toFixed(1));
    if (maxElem) maxElem.textContent = metric === "ram" ? `${Math.round(maxV)} MB` : (metric === "cpu" ? `${maxV.toFixed(1)}%` : maxV.toFixed(1));

    const coords = renderPoints.map(pt => {
        const val = metric === "tps" ? pt.tps : (metric === "ram" ? pt.ramUsedMb : pt.cpuPct);
        const xPct = rangeMs > 0 ? (pt.timestamp - cutoff) / rangeMs : 1;
        const x = padding.left + Math.max(0, Math.min(1, xPct)) * plotW;
        const yPct = (val - yMin) / (yMax - yMin || 1);
        const y = padding.top + plotH - Math.max(0, Math.min(1, yPct)) * plotH;
        return { x, y, val };
    });

    ctx.fillStyle = fillColor;
    ctx.beginPath();
    ctx.moveTo(coords[0].x, padding.top + plotH);
    coords.forEach(pt => ctx.lineTo(pt.x, pt.y));
    ctx.lineTo(coords[coords.length - 1].x, padding.top + plotH);
    ctx.closePath();
    ctx.fill();

    ctx.strokeStyle = lineColor;
    ctx.lineWidth = 2.5;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.beginPath();
    coords.forEach((pt, i) => {
        if (i === 0) ctx.moveTo(pt.x, pt.y);
        else ctx.lineTo(pt.x, pt.y);
    });
    ctx.stroke();

    const last = coords[coords.length - 1];
    ctx.fillStyle = lineColor;
    ctx.beginPath();
    ctx.arc(last.x, last.y, 4.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 2;
    ctx.stroke();
}

function initFlippableCards() {
    document.querySelectorAll('.metric-flip-card').forEach(card => {
        const front = card.querySelector('.metric-face-front');
        const back = card.querySelector('.metric-face-back');
        const inner = card.querySelector('.metric-flip-inner');
        const metric = card.getAttribute('data-metric');

        if (front && front.getAttribute('data-flip-bound') !== 'true') {
            front.setAttribute('data-flip-bound', 'true');

            const toggleFlip = () => {
                const isFlipped = card.classList.toggle('flipped');
                front.setAttribute('aria-pressed', isFlipped ? 'true' : 'false');
                if (isFlipped) {
                    renderMetricChart(metric);
                    setTimeout(() => renderMetricChart(metric), 60);
                    setTimeout(() => renderMetricChart(metric), 360);
                    syncHistoryFromServer().then(() => {
                        renderMetricChart(metric);
                    });
                }
            };

            front.addEventListener('click', toggleFlip);

            front.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    toggleFlip();
                }
            });
        }

        if (back && back.getAttribute('data-flip-back-bound') !== 'true') {
            back.setAttribute('data-flip-back-bound', 'true');

            back.addEventListener('click', (e) => {
                if (e.target.closest('.btn-time-pill') || e.target.closest('.cartesian-time-selector')) {
                    return;
                }
                card.classList.remove('flipped');
                if (front) front.setAttribute('aria-pressed', 'false');
            });
        }

        if (inner && inner.getAttribute('data-trans-bound') !== 'true') {
            inner.setAttribute('data-trans-bound', 'true');
            inner.addEventListener('transitionend', (e) => {
                if (e.propertyName === 'transform' && card.classList.contains('flipped')) {
                    renderMetricChart(metric);
                }
            });
        }
    });

    document.querySelectorAll('.btn-flip-back').forEach(btn => {
        if (btn.getAttribute('data-flip-bound') === 'true') return;
        btn.setAttribute('data-flip-bound', 'true');

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const card = btn.closest('.metric-flip-card');
            if (card) {
                card.classList.remove('flipped');
                const front = card.querySelector('.metric-face-front');
                if (front) front.setAttribute('aria-pressed', 'false');
            }
        });
    });

    document.querySelectorAll('.btn-time-pill').forEach(btn => {
        if (btn.getAttribute('data-flip-bound') === 'true') return;
        btn.setAttribute('data-flip-bound', 'true');

        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const card = btn.closest('.metric-flip-card');
            const metric = btn.getAttribute('data-metric') || (card ? card.getAttribute('data-metric') : null);
            const range = btn.getAttribute('data-range') || '1m';
            const parent = btn.closest('.cartesian-time-selector');
            if (parent) {
                parent.querySelectorAll('.btn-time-pill').forEach(b => b.classList.remove('active'));
            }
            btn.classList.add('active');
            if (metric) {
                activeChartRanges[metric] = range;
                renderMetricChart(metric);
            }
        });
    });

    document.querySelectorAll('.cartesian-time-selector').forEach(sel => {
        sel.addEventListener('click', (e) => e.stopPropagation());
    });

    window.addEventListener('resize', () => {
        ['tps', 'ram', 'cpu'].forEach(metric => {
            const card = document.getElementById(`card${metric.charAt(0).toUpperCase() + metric.slice(1)}`);
            if (card && card.classList.contains('flipped')) {
                renderMetricChart(metric);
            }
        });
    });
}



function renderActiveCharts() {
    ["tps", "ram", "cpu"].forEach(metric => {
        const cap = metric.charAt(0).toUpperCase() + metric.slice(1);
        const card = document.getElementById("card" + cap);
        if (card && card.classList.contains("flipped") && typeof renderMetricChart === "function") {
            renderMetricChart(metric);
        }
    });
}

window.currentView = currentView;
window.activeServerId = activeServerId;
window.activeServerName = activeServerName;
window.isCurtainTransitioning = isCurtainTransitioning;
window.isTelemetryPaused = isTelemetryPaused;
window.metricHistory = metricHistory;
window.activeChartRanges = activeChartRanges;
window.getTelemetryInterval = getTelemetryInterval;
window.startTelemetryPolling = startTelemetryPolling;
window.stopTelemetryPolling = stopTelemetryPolling;
window.toggleLiveTelemetry = toggleLiveTelemetry;
window.refreshCurrentView = refreshCurrentView;
window.fetchNetworkServers = fetchNetworkServers;
window.renderNetworkServersList = renderNetworkServersList;
window.runCurtainTransition = runCurtainTransition;
window.openServerDetails = openServerDetails;
window.transitionToServer = transitionToServer;
window.switchToNetworkHub = switchToNetworkHub;
window.switchToServerView = switchToServerView;
window.switchToHubView = switchToHubView;
window.fetchLiveStats = fetchLiveStats;
window.updateDashboardUI = updateDashboardUI;
window.syncHistoryFromServer = syncHistoryFromServer;
window.renderMetricChart = renderMetricChart;
window.renderActiveCharts = renderActiveCharts;
window.initFlippableCards = initFlippableCards;
