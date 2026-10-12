/* ==========================================================================
   PineApple Web Monitor - Multi-Language (i18n) & Localization
   ========================================================================== */

const TRANSLATIONS = {
    "en": {
        "hero_badge": "Paper 1.21.4 Monitor",
        "hero_tagline": "Real-time Minecraft telemetry and resource monitor.",
        "hero_feat_1": "Real-time TPS & MSPT",
        "hero_feat_2": "JVM Heap & Host CPU",
        "auth_subtitle": "Paper Console Access",
        "auth_title": "Server Authentication",
        "label_login": "Operator Username",
        "placeholder_login": "e.g. admin",
        "label_password": "Password",
        "placeholder_password": "Enter password",
        "label_remember_me": "Remember this session",
        "btn_submit_login": "Access Monitor",
        "host_config_title": "Paper Server Config",
        "host_config_desc": "Credentials and network options are in plugins/PineAppleMonitor/config.yml.",
        "nav_logout": "Sign Out",
        "dash_server_title": "Minecraft Server:",
        "dash_online_players": "Players:",
        "dash_uptime_label": "Uptime:",
        "status_live": "LIVE",
        "status_paused": "PAUSED",
        "btn_pause": "Pause Stream",
        "btn_resume": "Resume Stream",
        "btn_refresh": "Refresh Now",
        "card_tps_title": "Server TPS",
        "label_tps_breakdown": "TPS (1m, 5m, 15m):",
        "label_tick_health": "Tick Health:",
        "badge_chart": "Chart",
        "time_1m": "1 min",
        "time_30m": "30 min",
        "time_1h": "1 hour",
        "card_ram_title": "JVM Heap Memory",
        "label_heap_committed": "Allocated Heap:",
        "label_system_ram": "Host Memory:",
        "card_cpu_title": "CPU Load",
        "label_process_cpu": "JVM Process CPU:",
        "label_system_cpu": "Host System Total:",
        "label_loaded_chunks": "Total Loaded Chunks:",
        "label_total_entities": "Total Entities:",
        "table_players_title": "Online Players Telemetry",
        "table_players_sub": "Live player session status and network latency",
        "th_avatar": "AVATAR",
        "th_player_name": "PLAYER",
        "th_world": "WORLD",
        "th_gamemode": "MODE",
        "th_ping": "PING",
        "no_players_online": "No players currently connected",
        "table_worlds_title": "Worlds & Chunk Generation",
        "table_worlds_sub": "Loaded chunks and active entity counts per world",
        "th_world_name": "WORLD NAME",
        "th_environment": "DIMENSION",
        "th_chunks": "CHUNKS",
        "th_entities": "ENTITIES",
        "chart_collecting_data": "Collecting telemetry data...",
        "chart_waiting_history": "Waiting for server history...",
        "toast_welcome_back": "Welcome to Paper Monitor, {user}!",
        "toast_login_success": "Authenticated successfully!",
        "toast_invalid_creds": "Invalid credentials. Check config.yml",
        "toast_live_updated": "Telemetry refreshed",
        "toast_polling_paused": "Stream paused",
        "toast_polling_resumed": "Stream resumed",
        "toast_tab_hidden": "Polling paused to save server resources",
        "toast_lang_switched": "Language changed to English",
        "btn_network_overview": "Network Overview",
        "hub_banner_title": "Minecraft Network:",
        "hub_active_servers": "Active Servers:",
        "hub_total_players": "Total Players:",
        "hub_telemetry_channel": "Channel:",
        "hub_section_title": "Network Servers",
        "hub_section_sub": "Select a server to view real-time performance telemetry, 3D flippable cards, and player activity",
        "lang_select_title": "Select Language",
        "lang_search_placeholder": "Search language...",
        "profile_badge": "User Profile",
        "profile_tagline": "User session management, credential security, and permission roles.",
        "profile_status_user": "User",
        "profile_status_role": "Privileges",
        "profile_subtitle": "Session Management",
        "profile_title": "User Profile",
        "profile_change_password": "Change Password",
        "label_old_password": "Current Password",
        "label_new_password": "New Password",
        "btn_save_password": "Save Password",
        "profile_permissions": "Access Rights",
        "btn_to_dashboard": "To Dashboard",
        "console_title": "Server Console & Logs",
        "console_sub": "Real-time streaming terminal output from Minecraft server"
    },
    "ru": {
        "lang_select_title": "Выберите язык",
        "hero_badge": "Монитор Paper 1.21.4",
        "hero_tagline": "Телеметрия сервера Minecraft и мониторинг ресурсов в реальном времени.",
        "hero_feat_1": "TPS и MSPT в реальном времени",
        "hero_feat_2": "Память JVM и нагрузка CPU хоста",
        "auth_subtitle": "Доступ к консоли Paper",
        "auth_title": "Авторизация сервера",
        "label_login": "Имя оператора",
        "placeholder_login": "напр. admin",
        "label_password": "Пароль",
        "placeholder_password": "Введи пароль",
        "label_remember_me": "Запомнить сессию",
        "btn_submit_login": "Войти в монитор",
        "host_config_title": "Конфигурация Paper сервера",
        "host_config_desc": "Учетные данные и сетевые параметры находятся в plugins/PineApple/config.yml.",
        "nav_logout": "Выйти",
        "dash_server_title": "Сервер Minecraft:",
        "dash_online_players": "Игроки:",
        "dash_uptime_label": "Аптайм:",
        "status_live": "ОНЛАЙН",
        "status_paused": "ПАУЗА",
        "btn_pause": "Приостановить",
        "btn_resume": "Возобновить",
        "btn_refresh": "Обновить сейчас",
        "card_tps_title": "TPS сервера",
        "label_tps_breakdown": "TPS (1 мин, 5 мин, 15 мин):",
        "label_tick_health": "Стабильность тиков:",
        "badge_chart": "График",
        "time_1m": "1 мин",
        "time_30m": "30 мин",
        "time_1h": "1 час",
        "card_ram_title": "Память JVM (Heap)",
        "label_heap_committed": "Выделено JVM:",
        "label_system_ram": "Память хоста:",
        "card_cpu_title": "Нагрузка CPU",
        "label_process_cpu": "Процесс JVM CPU:",
        "label_system_cpu": "Общая нагрузка хоста:",
        "label_loaded_chunks": "Загружено чанков:",
        "label_total_entities": "Всего сущностей:",
        "table_players_title": "Телеметрия игроков онлайн",
        "table_players_sub": "Статус сессий игроков и сетевой пинг в реальном времени",
        "th_avatar": "СКИН",
        "th_player_name": "ИГРОК",
        "th_world": "МИР",
        "th_gamemode": "РЕЖИМ",
        "th_ping": "ПИНГ",
        "no_players_online": "Сейчас на сервере нет игроков",
        "table_worlds_title": "Миры и генерация чанков",
        "table_worlds_sub": "Количество загруженных чанков и активных сущностей по мирам",
        "th_world_name": "ИМЯ МИРА",
        "th_environment": "ИЗМЕРЕНИЕ",
        "th_chunks": "ЧАНКИ",
        "th_entities": "СУЩНОСТИ",
        "chart_collecting_data": "Сбор данных телеметрии...",
        "chart_waiting_history": "Ожидание истории с сервера...",
        "toast_welcome_back": "Добро пожаловать в монитор Paper, {user}!",
        "toast_login_success": "Успешная авторизация!",
        "toast_invalid_creds": "Неверные данные. Проверьте config.yml",
        "toast_live_updated": "Телеметрия обновлена",
        "toast_polling_paused": "Обновление приостановлено",
        "toast_polling_resumed": "Обновление возобновлено",
        "toast_tab_hidden": "Приостановлено для экономии ресурсов сервера",
        "btn_network_overview": "Обзор сети",
        "hub_banner_title": "Сеть Minecraft:",
        "hub_active_servers": "Активные серверы:",
        "hub_total_players": "Всего игроков:",
        "hub_telemetry_channel": "Канал:",
        "hub_section_title": "Серверы сети",
        "hub_section_sub": "Выберите сервер для просмотра телеметрии в реальном времени, 3D-карточек и активности игроков",
        "profile_badge": "Профиль пользователя",
        "profile_tagline": "Управление сессией пользователя, смена пароля и матрица уровней доступа сети.",
        "profile_status_user": "Пользователь",
        "profile_status_role": "Привилегии",
        "profile_subtitle": "Управление сессией",
        "profile_title": "Профиль пользователя",
        "profile_change_password": "Сменить пароль",
        "label_old_password": "Текущий пароль",
        "label_new_password": "Новый пароль",
        "btn_save_password": "Сохранить пароль",
        "profile_permissions": "Права доступа",
        "btn_to_dashboard": "В дешборд",
        "console_title": "Консоль и логи сервера",
        "console_sub": "Поток терминала в реальном времени с Paper сервера",
        "lang_search_placeholder": "Поиск языка...",
        "toast_lang_switched": "Язык изменён на русский"
    },
    "zh": {
        "hero_badge": "Paper 1.21.4 监控器",
        "hero_tagline": "实时 Minecraft 服务器遥测与资源监控平台。",
        "hero_feat_1": "实时 TPS 与 MSPT",
        "hero_feat_2": "JVM 堆内存与主机 CPU",
        "auth_subtitle": "Paper 控制台访问",
        "auth_title": "服务器身份验证",
        "label_login": "管理员账号",
        "placeholder_login": "例如 admin",
        "label_password": "密码",
        "placeholder_password": "输入密码",
        "label_remember_me": "记住此会话",
        "btn_submit_login": "进入监控器",
        "host_config_title": "Paper 服务器配置",
        "host_config_desc": "认证与网络选项位于 plugins/PineAppleMonitor/config.yml。",
        "nav_logout": "退出登录",
        "dash_server_title": "Minecraft 服务器：",
        "dash_online_players": "在线玩家：",
        "dash_uptime_label": "运行时间：",
        "status_live": "实时",
        "status_paused": "已暂停",
        "btn_pause": "暂停流",
        "btn_resume": "继续流",
        "btn_refresh": "立即刷新",
        "card_tps_title": "服务器 TPS",
        "label_tps_breakdown": "TPS (1分, 5分, 15分)：",
        "label_tick_health": "Tick 健康状态：",
        "badge_chart": "图表",
        "time_1m": "1 分钟",
        "time_30m": "30 分钟",
        "time_1h": "1 小时",
        "card_ram_title": "JVM 堆内存",
        "label_heap_committed": "已分配内存：",
        "label_system_ram": "主机系统内存：",
        "card_cpu_title": "CPU 负载",
        "label_process_cpu": "JVM 进程 CPU：",
        "label_system_cpu": "主机系统总负载：",
        "label_loaded_chunks": "总加载区块：",
        "label_total_entities": "实体总数：",
        "table_players_title": "在线玩家遥测",
        "table_players_sub": "实时玩家会话状态与网络延迟",
        "th_avatar": "头像",
        "th_player_name": "玩家",
        "th_world": "世界",
        "th_gamemode": "模式",
        "th_ping": "延迟",
        "no_players_online": "当前暂无在线玩家",
        "table_worlds_title": "世界与区块生成",
        "table_worlds_sub": "各世界的加载区块及活跃实体数量",
        "th_world_name": "世界名称",
        "th_environment": "维度",
        "th_chunks": "加载区块",
        "th_entities": "实体数量",
        "chart_collecting_data": "正在收集遥测数据...",
        "chart_waiting_history": "等待服务器历史数据...",
        "toast_welcome_back": "欢迎使用 Paper 监控器，{user}！",
        "toast_login_success": "认证成功！",
        "toast_invalid_creds": "认证失败，请检查 config.yml",
        "toast_live_updated": "遥测数据已更新",
        "toast_polling_paused": "遥测流已暂停",
        "toast_polling_resumed": "遥测流已恢复",
        "toast_tab_hidden": "后台已暂停以节省服务器资源",
        "toast_lang_switched": "已切换语言为中文",
        "btn_network_overview": "网络总览",
        "hub_banner_title": "Minecraft 网络：",
        "hub_active_servers": "活跃服务器：",
        "hub_total_players": "总玩家数：",
        "hub_telemetry_channel": "通信信道：",
        "hub_section_title": "网络服务器",
        "hub_section_sub": "选择服务器以查看实时性能遥测、3D 卡片与玩家动态",
        "lang_select_title": "选择语言",
        "lang_search_placeholder": "搜索语言...",
        "profile_badge": "用户资料",
        "profile_tagline": "管理用户会话、更改密码与网络权限控制矩阵。",
        "profile_status_user": "用户",
        "profile_status_role": "特权级别",
        "profile_subtitle": "会话管理",
        "profile_title": "用户个人资料",
        "profile_change_password": "修改密码",
        "label_old_password": "当前密码",
        "label_new_password": "新密码",
        "btn_save_password": "保存密码",
        "profile_permissions": "访问权限",
        "btn_to_dashboard": "返回控制台",
        "console_title": "服务器控制台与日志",
        "console_sub": "来自Minecraft服务器的实时终端输出流"
    },
    "fr": {
        "hero_badge": "Moniteur Paper 1.21.4",
        "hero_tagline": "Télémétrie Minecraft et moniteur de ressources en temps réel.",
        "hero_feat_1": "TPS et MSPT en temps réel",
        "hero_feat_2": "JVM Heap et CPU de l'hôte",
        "auth_subtitle": "Accès Console Paper",
        "auth_title": "Authentification Serveur",
        "label_login": "Nom d'utilisateur",
        "placeholder_login": "ex. admin",
        "label_password": "Mot de passe",
        "placeholder_password": "Entrez le mot de passe",
        "label_remember_me": "Mémoriser cette session",
        "btn_submit_login": "Accéder au Moniteur",
        "host_config_title": "Configuration Serveur Paper",
        "host_config_desc": "Identifiants et options réseau dans plugins/PineAppleMonitor/config.yml.",
        "nav_logout": "Déconnexion",
        "dash_server_title": "Serveur Minecraft :",
        "dash_online_players": "Joueurs :",
        "dash_uptime_label": "Temps de fonctionnement :",
        "status_live": "EN DIRECT",
        "status_paused": "EN PAUSE",
        "btn_pause": "Mettre en pause",
        "btn_resume": "Reprendre",
        "btn_refresh": "Actualiser",
        "card_tps_title": "TPS du Serveur",
        "label_tps_breakdown": "TPS (1m, 5m, 15m) :",
        "label_tick_health": "Santé du Tick :",
        "badge_chart": "Graphique",
        "time_1m": "1 min",
        "time_30m": "30 min",
        "time_1h": "1 heure",
        "card_ram_title": "Mémoire JVM Heap",
        "label_heap_committed": "Heap alloué :",
        "label_system_ram": "Mémoire système :",
        "card_cpu_title": "Charge CPU",
        "label_process_cpu": "CPU Processus JVM :",
        "label_system_cpu": "CPU Système total :",
        "label_loaded_chunks": "Total Chunks chargés :",
        "label_total_entities": "Total Entités :",
        "table_players_title": "Télémétrie des Joueurs",
        "table_players_sub": "État des sessions et latence réseau en direct",
        "th_avatar": "AVATAR",
        "th_player_name": "JOUEUR",
        "th_world": "MONDE",
        "th_gamemode": "MODE",
        "th_ping": "PING",
        "no_players_online": "Aucun joueur connecté",
        "table_worlds_title": "Mondes et Génération de Chunks",
        "table_worlds_sub": "Chunks chargés et entités actives par monde",
        "th_world_name": "NOM DU MONDE",
        "th_environment": "DIMENSION",
        "th_chunks": "CHUNKS",
        "th_entities": "ENTITÉS",
        "chart_collecting_data": "Collecte des données...",
        "chart_waiting_history": "En attente de l'historique...",
        "toast_welcome_back": "Bienvenue sur le Moniteur Paper, {user} !",
        "toast_login_success": "Authentifié avec succès !",
        "toast_invalid_creds": "Identifiants invalides. Vérifiez config.yml",
        "toast_live_updated": "Télémétrie actualisée",
        "toast_polling_paused": "Flux en pause",
        "toast_polling_resumed": "Flux repris",
        "toast_tab_hidden": "En pause pour économiser les ressources",
        "toast_lang_switched": "Langue changée en Français",
        "btn_network_overview": "Vue Réseau",
        "hub_banner_title": "Réseau Minecraft :",
        "hub_active_servers": "Serveurs Actifs :",
        "hub_total_players": "Total Joueurs :",
        "hub_telemetry_channel": "Canal :",
        "hub_section_title": "Serveurs du Réseau",
        "hub_section_sub": "Sélectionnez un serveur pour afficher la télémétrie en temps réel, les cartes 3D et les joueurs",
        "lang_select_title": "Choisir la langue",
        "lang_search_placeholder": "Rechercher une langue...",
        "profile_badge": "Profil Utilisateur",
        "profile_tagline": "Gestion de session, modification du mot de passe et matrice de droits d'accès.",
        "profile_status_user": "Utilisateur",
        "profile_status_role": "Privilèges",
        "profile_subtitle": "Gestion de Session",
        "profile_title": "Profil Utilisateur",
        "profile_change_password": "Changer le mot de passe",
        "label_old_password": "Mot de passe actuel",
        "label_new_password": "Nouveau mot de passe",
        "btn_save_password": "Enregistrer le mot de passe",
        "profile_permissions": "Droits d'accès",
        "btn_to_dashboard": "Au Tableau de bord",
        "console_title": "Console et Journaux Serveur",
        "console_sub": "Flux de terminal en temps réel du serveur Minecraft"
    },
    "es": {
        "hero_badge": "Monitor Paper 1.21.4",
        "hero_tagline": "Telemetría de Minecraft y monitor de recursos en tiempo real.",
        "hero_feat_1": "TPS y MSPT en tiempo real",
        "hero_feat_2": "JVM Heap y CPU del Servidor",
        "auth_subtitle": "Acceso a la Consola de Paper",
        "auth_title": "Autenticación del Servidor",
        "label_login": "Nombre de Usuario",
        "placeholder_login": "ej. admin",
        "label_password": "Contraseña",
        "placeholder_password": "Introduce tu contraseña",
        "label_remember_me": "Recordar esta sesión",
        "btn_submit_login": "Acceder al Monitor",
        "host_config_title": "Configuración del Servidor Paper",
        "host_config_desc": "Credenciales y opciones de red en plugins/PineAppleMonitor/config.yml.",
        "nav_logout": "Cerrar Sesión",
        "dash_server_title": "Servidor Minecraft:",
        "dash_online_players": "Jugadores:",
        "dash_uptime_label": "Tiempo activo:",
        "status_live": "EN VIVO",
        "status_paused": "PAUSADO",
        "btn_pause": "Pausar Flujo",
        "btn_resume": "Reanudar Flujo",
        "btn_refresh": "Actualizar",
        "card_tps_title": "TPS del Servidor",
        "label_tps_breakdown": "TPS (1m, 5m, 15m):",
        "label_tick_health": "Salud del Tick:",
        "badge_chart": "Gráfico",
        "time_1m": "1 min",
        "time_30m": "30 min",
        "time_1h": "1 hora",
        "card_ram_title": "Memoria JVM Heap",
        "label_heap_committed": "Heap Asignado:",
        "label_system_ram": "Memoria del Sistema:",
        "card_cpu_title": "Uso de CPU",
        "label_process_cpu": "CPU Proceso JVM:",
        "label_system_cpu": "CPU Total del Sistema:",
        "label_loaded_chunks": "Chunks Cargados:",
        "label_total_entities": "Entidades Totales:",
        "table_players_title": "Telemetría de Jugadores",
        "table_players_sub": "Estado de sesión y latencia de red en vivo",
        "th_avatar": "AVATAR",
        "th_player_name": "JUGADOR",
        "th_world": "MUNDO",
        "th_gamemode": "MODO",
        "th_ping": "PING",
        "no_players_online": "No hay jugadores conectados",
        "table_worlds_title": "Mundos y Generación de Chunks",
        "table_worlds_sub": "Chunks cargados y recuento de entidades por mundo",
        "th_world_name": "NOMBRE DEL MUNDO",
        "th_environment": "DIMENSIÓN",
        "th_chunks": "CHUNKS",
        "th_entities": "ENTIDADES",
        "chart_collecting_data": "Recolectando datos de telemetría...",
        "chart_waiting_history": "Esperando historial del servidor...",
        "toast_welcome_back": "¡Bienvenido al Monitor Paper, {user}!",
        "toast_login_success": "¡Autenticado con éxito!",
        "toast_invalid_creds": "Credenciales inválidas. Revisa config.yml",
        "toast_live_updated": "Telemetría actualizada",
        "toast_polling_paused": "Flujo pausado",
        "toast_polling_resumed": "Flujo reanudado",
        "toast_tab_hidden": "Pausa para ahorrar recursos del servidor",
        "toast_lang_switched": "Idioma cambiado a Español",
        "btn_network_overview": "Visión de Red",
        "hub_banner_title": "Red de Minecraft:",
        "hub_active_servers": "Servidores Activos:",
        "hub_total_players": "Jugadores Totales:",
        "hub_telemetry_channel": "Canal:",
        "hub_section_title": "Servidores de la Red",
        "hub_section_sub": "Selecciona un servidor para ver telemetría en tiempo real, tarjetas 3D y jugadores",
        "lang_select_title": "Seleccionar Idioma",
        "lang_search_placeholder": "Buscar idioma...",
        "profile_badge": "Perfil de Usuario",
        "profile_tagline": "Gestión de sesión, cambio de contraseña y permisos de la red.",
        "profile_status_user": "Usuario",
        "profile_status_role": "Privilegios",
        "profile_subtitle": "Gestión de Sesión",
        "profile_title": "Perfil de Usuario",
        "profile_change_password": "Cambiar Contraseña",
        "label_old_password": "Contraseña Actual",
        "label_new_password": "Nueva Contraseña",
        "btn_save_password": "Guardar Contraseña",
        "profile_permissions": "Permisos de Acceso",
        "btn_to_dashboard": "Al Panel",
        "console_title": "Consola y Registros del Servidor",
        "console_sub": "Salida de terminal en tiempo real del servidor Minecraft"
    },
    "ar": {
        "hero_badge": "مراقب Paper 1.21.4",
        "hero_tagline": "مراقبة موارد وخادم ماين كرافت عن بُعد في الوقت الفعلي.",
        "hero_feat_1": "معدل TPS و MSPT المباشر",
        "hero_feat_2": "ذاكرة JVM Heap ومعالج النظام",
        "auth_subtitle": "الوصول إلى وحدة تحكم Paper",
        "auth_title": "مصادقة الخادم",
        "label_login": "اسم المشرف",
        "placeholder_login": "مثال: admin",
        "label_password": "كلمة المرور",
        "placeholder_password": "أدخل كلمة المرور",
        "label_remember_me": "تذكر هذه الجلسة",
        "btn_submit_login": "دخول المراقب",
        "host_config_title": "إعدادات خادم Paper",
        "host_config_desc": "بيانات الدخول وخيارات الشبكة في plugins/PineAppleMonitor/config.yml.",
        "nav_logout": "تسجيل الخروج",
        "dash_server_title": "خادم ماين كرافت:",
        "dash_online_players": "اللاعبون:",
        "dash_uptime_label": "وقت التشغيل:",
        "status_live": "مباشر",
        "status_paused": "مؤقت",
        "btn_pause": "إيقاف مؤقت",
        "btn_resume": "استئناف البث",
        "btn_refresh": "تحديث الآن",
        "card_tps_title": "معدل TPS للخادم",
        "label_tps_breakdown": "TPS (1د, 5د, 15د):",
        "label_tick_health": "صحة التيك:",
        "badge_chart": "مخطط",
        "time_1m": "1 دقيقة",
        "time_30m": "30 دقيقة",
        "time_1h": "ساعة واحدة",
        "card_ram_title": "ذاكرة JVM Heap",
        "label_heap_committed": "الذاكرة المخصصة:",
        "label_system_ram": "ذاكرة النظام المضيف:",
        "card_cpu_title": "استهلاك المعالج",
        "label_process_cpu": "معالج عملية JVM:",
        "label_system_cpu": "إجمالي معالج النظام:",
        "label_loaded_chunks": "إجمالي التشانكات المحملة:",
        "label_total_entities": "إجمالي الكائنات:",
        "table_players_title": "بيانات اللاعبين المتصلين",
        "table_players_sub": "حالة الجلسة المباشرة وزمن الاستجابة",
        "th_avatar": "الصورة",
        "th_player_name": "اللاعب",
        "th_world": "العالم",
        "th_gamemode": "النمط",
        "th_ping": "البينغ",
        "no_players_online": "لا يوجد لاعبون متصلون حالياً",
        "table_worlds_title": "العوالم وتوليد التشانكات",
        "table_worlds_sub": "التشانكات والكائنات المحملة لكل عالم",
        "th_world_name": "اسم العالم",
        "th_environment": "البعد",
        "th_chunks": "تشانكات",
        "th_entities": "الكائنات",
        "chart_collecting_data": "جاري جمع البيانات...",
        "chart_waiting_history": "بانتظار سجل الخادم...",
        "toast_welcome_back": "مرحباً بك في مراقب Paper، {user}!",
        "toast_login_success": "تمت المصادقة بنجاح!",
        "toast_invalid_creds": "بيانات الدخول غير صحيحة. تحقق من config.yml",
        "toast_live_updated": "تم تحديث البيانات",
        "toast_polling_paused": "تم إيقاف التحديث مؤقتاً",
        "toast_polling_resumed": "تم استئناف التحديث",
        "toast_tab_hidden": "تم الإيقاف لتوفير موارد الخادم",
        "toast_lang_switched": "تم تغيير اللغة إلى العربية",
        "btn_network_overview": "نظرة عامة على الشبكة",
        "hub_banner_title": "شبكة ماين كرافت:",
        "hub_active_servers": "الخوادم النشطة:",
        "hub_total_players": "إجمالي اللاعبين:",
        "hub_telemetry_channel": "القناة:",
        "hub_section_title": "خوادم الشبكة",
        "hub_section_sub": "اختر خادماً لمشاهدة قياسات الأداء وبطاقات 3D ونشاط اللاعبين",
        "lang_select_title": "اختر اللغة",
        "lang_search_placeholder": "ابحث عن لغة...",
        "profile_badge": "الملف الشخصي",
        "profile_tagline": "إدارة جلسة المستخدم وتغيير كلمة المرور ومصفوفة صلاحيات الشبكة.",
        "profile_status_user": "المستخدم",
        "profile_status_role": "الصلاحيات",
        "profile_subtitle": "إدارة الجلسة",
        "profile_title": "ملف المستخدم",
        "profile_change_password": "تغيير كلمة المرور",
        "label_old_password": "كلمة المرور الحالية",
        "label_new_password": "كلمة المرور الجديدة",
        "btn_save_password": "حفظ كلمة المرور",
        "profile_permissions": "صلاحيات الوصول",
        "btn_to_dashboard": "إلى لوحة التحكم",
        "console_title": "وحدة تحكم وسجلات الخادم",
        "console_sub": "بث مباشر لمخرجات الطرفية من خادم ماين كرافت"
    }
};

const FLAG_SVGS = {
    en: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><path d="M0 0 h60 v45 H0 z" fill="#012169"/><path d="M0 0 L60 45 M60 0 L0 45" stroke="#ffffff" stroke-width="8"/><path d="M0 0 L60 45 M60 0 L0 45" stroke="#C8102E" stroke-width="4.8"/><path d="M30 0 v45 M0 22.5 h60" stroke="#ffffff" stroke-width="14"/><path d="M30 0 v45 M0 22.5 h60" stroke="#C8102E" stroke-width="8.4"/></svg>`,
    ru: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><rect width="60" height="15" fill="#ffffff"/><rect y="15" width="60" height="15" fill="#0039A6"/><rect y="30" width="60" height="15" fill="#D52B1E"/></svg>`,
    zh: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><rect width="60" height="45" fill="#DE2910"/><polygon points="12,6 13.9,11.8 19.9,11.8 15,15.4 16.9,21.2 12,17.6 7.1,21.2 9,15.4 4.1,11.8 10.1,11.8" fill="#FFDE00"/><circle cx="21" cy="6.5" r="1.5" fill="#FFDE00"/><circle cx="25" cy="10.5" r="1.5" fill="#FFDE00"/><circle cx="25" cy="16" r="1.5" fill="#FFDE00"/><circle cx="21" cy="20" r="1.5" fill="#FFDE00"/></svg>`,
    fr: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><rect width="20" height="45" fill="#002654"/><rect x="20" width="20" height="45" fill="#ffffff"/><rect x="40" width="20" height="45" fill="#ED2939"/></svg>`,
    es: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><rect width="60" height="11.25" fill="#AA151B"/><rect y="11.25" width="60" height="22.5" fill="#F1BF00"/><rect y="33.75" width="60" height="11.25" fill="#AA151B"/><rect x="15" y="18" width="6" height="8" rx="1.5" fill="#AA151B"/></svg>`,
    ar: `<svg class="flag-svg" viewBox="0 0 60 45" xmlns="http://www.w3.org/2000/svg"><rect width="60" height="45" fill="#006C35"/><path d="M14 29 h32 M41 26.5 l5 2.5 -5 2.5" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/><text x="30" y="21" font-family="sans-serif" font-size="11" font-weight="bold" fill="#ffffff" text-anchor="middle">عرب</text></svg>`
};

const SUPPORTED_LANGUAGES = [
    { code: "en", name: "English", native: "English", flag: FLAG_SVGS.en },
    { code: "ru", name: "Russian", native: "Русский", flag: FLAG_SVGS.ru },
    { code: "zh", name: "Chinese", native: "简体中文", flag: FLAG_SVGS.zh },
    { code: "fr", name: "French", native: "Français", flag: FLAG_SVGS.fr },
    { code: "es", name: "Spanish", native: "Español", flag: FLAG_SVGS.es },
    { code: "ar", name: "Arabic", native: "العربية", flag: FLAG_SVGS.ar }
];

function getLanguage() {
    return localStorage.getItem("pineapple_lang") || "en";
}

function t(key, fallback = "") {
    const lang = getLanguage();
    const dict = TRANSLATIONS[lang] || TRANSLATIONS.en || TRANSLATIONS.ru;
    return (dict && dict[key]) || fallback || key;
}

function applyLanguage(lang) {
    const validCodes = ["en", "ru", "zh", "fr", "es", "ar"];
    const activeLang = validCodes.includes(lang) ? lang : "en";
    localStorage.setItem("pineapple_lang", activeLang);
    document.documentElement.lang = activeLang;

    document.querySelectorAll(".lang-code-text").forEach(el => {
        el.textContent = activeLang.toUpperCase();
    });

    const flagSvg = FLAG_SVGS[activeLang] || "";
    document.querySelectorAll(".lang-flag-badge").forEach(el => {
        if (!el.closest(".lang-option-item")) {
            el.innerHTML = flagSvg;
        }
    });

    const dict = TRANSLATIONS[activeLang] || TRANSLATIONS.en;
    if (dict) {
        document.querySelectorAll("[data-i18n]").forEach(el => {
            const key = el.getAttribute("data-i18n");
            if (dict[key]) {
                el.textContent = dict[key];
            }
        });

        document.querySelectorAll("[data-i18n-placeholder]").forEach(el => {
            const key = el.getAttribute("data-i18n-placeholder");
            if (dict[key]) {
                el.setAttribute("placeholder", dict[key]);
            }
        });

        document.querySelectorAll("[data-i18n-title]").forEach(el => {
            const key = el.getAttribute("data-i18n-title");
            if (dict[key]) {
                el.setAttribute("title", dict[key]);
            }
        });
    }

    document.querySelectorAll(".lang-option-item").forEach(btn => {
        if (btn.getAttribute("data-lang") === activeLang) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });

    renderActiveCharts();
}
window.applyLanguage = applyLanguage;

function toggleLanguageDropdown(event, suffix) {
    if (event) {
        event.stopPropagation();
        event.preventDefault();
    }
    const wrapper = document.getElementById("langDropdownWrapper" + suffix);
    if (!wrapper) return;
    const wasOpen = wrapper.classList.contains("open");
    closeAllLanguageDropdowns();
    if (!wasOpen) {
        wrapper.classList.add("open");
    }
}
window.toggleLanguageDropdown = toggleLanguageDropdown;

function closeAllLanguageDropdowns() {
    document.querySelectorAll(".lang-dropdown-wrapper").forEach(w => w.classList.remove("open"));
}
window.closeAllLanguageDropdowns = closeAllLanguageDropdowns;

function initLanguageDropdowns() {
    ["Auth", "Dash"].forEach(suffix => {
        const list = document.getElementById("langDropdownList" + suffix);
        if (!list) return;
        const currentLang = getLanguage();
        list.innerHTML = "";
        SUPPORTED_LANGUAGES.forEach(item => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = `lang-option-item ${item.code === currentLang ? "active" : ""}`;
            btn.setAttribute("data-lang", item.code);
            btn.onclick = (e) => {
                e.stopPropagation();
                e.preventDefault();
                applyLanguage(item.code);
                closeAllLanguageDropdowns();
            };
            btn.innerHTML = `
                <span class="lang-flag-badge">${item.flag}</span>
                <span class="lang-option-text">
                    <span class="lang-option-name">${item.name}</span>
                    <span class="lang-option-native">${item.native}</span>
                </span>
                <i class="fa-solid fa-check lang-option-check"></i>
            `;
            list.appendChild(btn);
        });
    });
}
window.initLanguageDropdowns = initLanguageDropdowns;

document.addEventListener("click", (e) => {
    if (!e.target.closest(".lang-dropdown-wrapper")) {
        closeAllLanguageDropdowns();
    }
});

function toggleLanguage() {
    const validCodes = ["en", "ru", "zh", "fr", "es", "ar"];
    const current = getLanguage();
    const idx = validCodes.indexOf(current);
    const next = validCodes[(idx + 1) % validCodes.length];
    applyLanguage(next);
}


window.TRANSLATIONS = TRANSLATIONS;
window.FLAG_SVGS = FLAG_SVGS;
window.SUPPORTED_LANGUAGES = SUPPORTED_LANGUAGES;
window.getLanguage = getLanguage;
window.t = t;
window.applyLanguage = applyLanguage;
window.toggleLanguageDropdown = toggleLanguageDropdown;
window.closeAllLanguageDropdowns = closeAllLanguageDropdowns;
window.initLanguageDropdowns = initLanguageDropdowns;
window.toggleLanguage = toggleLanguage;
