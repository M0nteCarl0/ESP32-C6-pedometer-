// ============================================================================
// ESP32-C6 Pedometer - Web Bluetooth Companion & Android Step Exporter
// ============================================================================

const BLE_UUIDS = {
    CUSTOM_SERVICE: "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
    CHAR_LIVE_DATA: "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
    CHAR_HISTORY:   "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
    CHAR_COMMAND:   "6e400004-b5a3-f393-e0a9-e50e24dcca9e",
    RSC_SERVICE:    "00001814-0000-1000-8000-00805f9b34fb",
    BATTERY_SERVICE:"0000180f-0000-1000-8000-00805f9b34fb"
};

// Application State
let bleDevice = null;
let gattServer = null;
let customService = null;
let charLive = null;
let charHistory = null;
let charCommand = null;

let currentData = {
    steps: 0,
    cadence: 100,
    speed: 0.0,
    dist: 0.0,
    kcal: 0,
    sec: 0,
    goal: 10000,
    mode: "PAUSED",
    hourly: new Array(24).fill(0),
    daily: new Array(7).fill(0)
};

// UI Elements
const btnConnect = document.getElementById("btnConnect");
const bleStatusBadge = document.getElementById("bleStatusBadge");
const bleStatusText = document.getElementById("bleStatusText");
const liveSteps = document.getElementById("liveSteps");
const liveDist = document.getElementById("liveDist");
const liveKcal = document.getElementById("liveKcal");
const liveCadence = document.getElementById("liveCadence");
const liveSpeed = document.getElementById("liveSpeed");
const modePill = document.getElementById("modePill");
const progressBarFill = document.getElementById("progressBarFill");
const goalPercentText = document.getElementById("goalPercentText");
const goalTargetText = document.getElementById("goalTargetText");
const cadenceSlider = document.getElementById("cadenceSlider");
const cadenceValDisplay = document.getElementById("cadenceValDisplay");
const terminalLog = document.getElementById("terminalLog");
const hourlyCanvas = document.getElementById("hourlyChart");

function log(msg) {
    const time = new Date().toLocaleTimeString();
    terminalLog.innerHTML = `[${time}] ${msg}\n` + terminalLog.innerHTML;
}

// Check Web Bluetooth API availability
if (!navigator.bluetooth) {
    btnConnect.disabled = true;
    btnConnect.innerHTML = "❌ Web Bluetooth не поддерживается в этом браузере";
    log("ОШИБКА: Web Bluetooth не поддерживается. Откройте в Google Chrome / Samsung Internet на Android.");
}

// ----------------------------------------------------------------------------
// BLE Connect & Event Handlers
// ----------------------------------------------------------------------------
btnConnect.addEventListener("click", async () => {
    if (bleDevice && bleDevice.gatt.connected) {
        disconnect();
        return;
    }

    try {
        log("Поиск устройства 'ESP32-C6-Pedometer'...");
        bleDevice = await navigator.bluetooth.requestDevice({
            filters: [{ name: "ESP32-C6-Pedometer" }],
            optionalServices: [
                BLE_UUIDS.CUSTOM_SERVICE,
                BLE_UUIDS.RSC_SERVICE,
                BLE_UUIDS.BATTERY_SERVICE
            ]
        });

        bleDevice.addEventListener('gattserverdisconnected', onDisconnected);

        log("Подключение к GATT серверу...");
        gattServer = await bleDevice.gatt.connect();

        log("Получение сервиса данных шагомера...");
        customService = await gattServer.getPrimaryService(BLE_UUIDS.CUSTOM_SERVICE);

        // Characteristics
        charLive = await customService.getCharacteristic(BLE_UUIDS.CHAR_LIVE_DATA);
        charHistory = await customService.getCharacteristic(BLE_UUIDS.CHAR_HISTORY);
        charCommand = await customService.getCharacteristic(BLE_UUIDS.CHAR_COMMAND);

        // Start Live Notifications
        await charLive.startNotifications();
        charLive.addEventListener('characteristicvaluechanged', handleLiveNotification);

        // Start History Notifications
        await charHistory.startNotifications();
        charHistory.addEventListener('characteristicvaluechanged', handleHistoryNotification);

        updateConnectionUI(true);
        log("✅ Успешно подключено к ESP32-C6! Поток данных активен.");

        // Request initial history
        setTimeout(() => sendCommand("REQ_HIST"), 500);

    } catch (err) {
        log(`Ошибка подключения: ${err.message}`);
        console.error(err);
        updateConnectionUI(false);
    }
});

function disconnect() {
    if (bleDevice && bleDevice.gatt.connected) {
        bleDevice.gatt.disconnect();
    }
}

function onDisconnected() {
    log("⚠️ Устройство ESP32-C6 отключено.");
    updateConnectionUI(false);
}

function updateConnectionUI(connected) {
    if (connected) {
        bleStatusBadge.className = "ble-status-badge connected";
        bleStatusText.innerText = "Подключено (ESP32-C6)";
        btnConnect.classList.remove("btn-primary");
        btnConnect.classList.add("btn-outline");
        btnConnect.innerHTML = "<span>Отключить</span>";
    } else {
        bleStatusBadge.className = "ble-status-badge disconnected";
        bleStatusText.innerText = "Отключено";
        btnConnect.classList.remove("btn-outline");
        btnConnect.classList.add("btn-primary");
        btnConnect.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m7 7 10 10-5 5V2l5 5L7 17"/></svg><span>Подключить шагомер (BLE)</span>`;
    }
}

// ----------------------------------------------------------------------------
// Data Parsing & UI Refresh
// ----------------------------------------------------------------------------
function handleLiveNotification(event) {
    const decoder = new TextDecoder('utf-8');
    const jsonStr = decoder.decode(event.target.value);
    
    try {
        const data = JSON.parse(jsonStr);
        currentData = { ...currentData, ...data };
        renderLiveUI();
    } catch (e) {
        console.warn("Invalid live JSON:", jsonStr);
    }
}

function handleHistoryNotification(event) {
    const decoder = new TextDecoder('utf-8');
    const jsonStr = decoder.decode(event.target.value);
    
    try {
        const hist = JSON.parse(jsonStr);
        if (hist.hourly) currentData.hourly = hist.hourly;
        if (hist.daily) currentData.daily = hist.daily;
        drawHourlyChart();
        log("📊 Почасовые данные активности успешно обновлены!");
    } catch (e) {
        console.warn("Invalid history JSON:", jsonStr);
    }
}

function renderLiveUI() {
    // Large Steps
    liveSteps.innerText = currentData.steps.toLocaleString('ru-RU');
    liveDist.innerText = currentData.dist.toFixed(2);
    liveKcal.innerText = Math.round(currentData.kcal);
    liveCadence.innerText = currentData.cadence;
    liveSpeed.innerText = currentData.speed.toFixed(1);
    modePill.innerText = currentData.mode;

    // Progress Bar
    const pct = Math.min(100, Math.round((currentData.steps / currentData.goal) * 100));
    progressBarFill.style.width = `${pct}%`;
    goalPercentText.innerText = `${pct}% от цели`;
    goalTargetText.innerText = `Цель: ${currentData.goal.toLocaleString('ru-RU')}`;

    // Mode Pill Color
    if (currentData.mode === "WALK") modePill.style.color = "var(--accent-green)";
    else if (currentData.mode === "JOG") modePill.style.color = "var(--accent-yellow)";
    else if (currentData.mode === "RUN") modePill.style.color = "var(--accent-orange)";
    else modePill.style.color = "var(--text-muted)";
}

// ----------------------------------------------------------------------------
// Canvas 24-Hour Activity Chart
// ----------------------------------------------------------------------------
function drawHourlyChart() {
    if (!hourlyCanvas) return;
    const ctx = hourlyCanvas.getContext("2d");
    const width = hourlyCanvas.width;
    const height = hourlyCanvas.height;

    ctx.clearRect(0, 0, width, height);

    const data = currentData.hourly || new Array(24).fill(0);
    const maxVal = Math.max(500, ...data);

    const padding = { top: 20, bottom: 30, left: 30, right: 15 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;
    const barWidth = (chartW / 24) - 4;

    // Draw Grid Lines
    ctx.strokeStyle = "#1e293b";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padding.left, padding.top + chartH);
    ctx.lineTo(width - padding.right, padding.top + chartH);
    ctx.moveTo(padding.left, padding.top + chartH / 2);
    ctx.lineTo(width - padding.right, padding.top + chartH / 2);
    ctx.stroke();

    // Bars
    data.forEach((val, hour) => {
        const barH = (val / maxVal) * chartH;
        const x = padding.left + (hour * (chartW / 24)) + 2;
        const y = padding.top + chartH - barH;

        // Gradient
        const grad = ctx.createLinearGradient(0, y, 0, padding.top + chartH);
        grad.addColorStop(0, val > 0 ? "#00ffa3" : "#334155");
        grad.addColorStop(1, val > 0 ? "#00e5ff" : "#1e293b");

        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.roundRect(x, y, barWidth, barH, [3, 3, 0, 0]);
        ctx.fill();

        // X Labels (every 4 hours)
        if (hour % 4 === 0) {
            ctx.fillStyle = "#64748b";
            ctx.font = "10px Inter";
            ctx.textAlign = "center";
            ctx.fillText(`${hour}:00`, x + barWidth / 2, height - 10);
        }
    });
}

// Initial dummy chart render
drawHourlyChart();

// ----------------------------------------------------------------------------
// Remote Command Execution
// ----------------------------------------------------------------------------
async function sendCommand(cmd) {
    if (!charCommand) {
        log("Команда не отправлена: нет BLE подключения.");
        return;
    }
    try {
        const encoder = new TextEncoder();
        await charCommand.writeValue(encoder.encode(cmd));
        log(`[TX] Отправлено: ${cmd}`);
    } catch (err) {
        log(`Ошибка отправки: ${err.message}`);
    }
}

// Mode Buttons
document.querySelectorAll(".btn-mode").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".btn-mode").forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        const cmd = btn.getAttribute("data-cmd");
        sendCommand(cmd);
    });
});

// Cadence Slider
cadenceSlider.addEventListener("input", (e) => {
    const val = e.target.value;
    cadenceValDisplay.innerText = `${val} шаг/мин`;
});
cadenceSlider.addEventListener("change", (e) => {
    sendCommand(`CAD:${e.target.value}`);
});

// Quick Add Step Buttons
document.querySelectorAll(".btn-quick[data-add]").forEach(btn => {
    btn.addEventListener("click", () => {
        const add = btn.getAttribute("data-add");
        sendCommand(`ADD:${add}`);
    });
});

document.getElementById("btnResetSteps").addEventListener("click", () => {
    if (confirm("Вы действительно хотите сбросить текущие шаги?")) {
        sendCommand("RESET");
    }
});

document.getElementById("btnSetGoal").addEventListener("click", () => {
    const goal = document.getElementById("inputCustomGoal").value;
    if (goal > 0) sendCommand(`GOAL:${goal}`);
});

document.getElementById("btnSyncTime").addEventListener("click", () => {
    const nowUnix = Math.floor(Date.now() / 1000);
    sendCommand(`TIME:${nowUnix}`);
    log(`Часы синхронизированы: UNIX ${nowUnix}`);
});

document.getElementById("btnRefreshHistory").addEventListener("click", () => {
    sendCommand("REQ_HIST");
});

// ----------------------------------------------------------------------------
// EXPORT FUNCTIONS (CSV, JSON, Clipboard)
// ----------------------------------------------------------------------------

// 1. Export CSV
document.getElementById("btnExportCsv").addEventListener("click", () => {
    const dateStr = new Date().toISOString().split("T")[0];
    let csv = "Час;Шаги;Дистанция_км;Калории_ккал\n";
    
    currentData.hourly.forEach((steps, h) => {
        const dist = (steps * 0.75 / 1000).toFixed(3);
        const kcal = (steps * 0.045).toFixed(1);
        csv += `${h}:00;${steps};${dist};${kcal}\n`;
    });

    csv += `\nИТОГО_ЗА_ДЕНЬ;${currentData.steps};${currentData.dist.toFixed(2)};${Math.round(currentData.kcal)}\n`;

    downloadFile(csv, `pedometer_steps_${dateStr}.csv`, "text/csv;charset=utf-8;");
    log(`📥 CSV файл 'pedometer_steps_${dateStr}.csv' успешно скачан!`);
});

// 2. Export JSON (Google Fit / Health Connect format)
document.getElementById("btnExportJson").addEventListener("click", () => {
    const dateStr = new Date().toISOString().split("T")[0];
    const exportObj = {
        device: "Waveshare ESP32-C6-LCD-1.47",
        exportDate: new Date().toISOString(),
        summary: {
            totalSteps: currentData.steps,
            totalDistanceKm: currentData.dist,
            totalCaloriesKcal: currentData.kcal,
            activeSeconds: currentData.sec,
            targetGoal: currentData.goal
        },
        hourlyBuckets: currentData.hourly.map((steps, hour) => ({
            hour: hour,
            startTime: `${dateStr}T${String(hour).padStart(2, '0')}:00:00Z`,
            endTime: `${dateStr}T${String(hour).padStart(2, '0')}:59:59Z`,
            stepCount: steps,
            distanceMeters: Math.round(steps * 0.75),
            caloriesBurned: Math.round(steps * 0.045)
        }))
    };

    const jsonStr = JSON.stringify(exportObj, null, 2);
    downloadFile(jsonStr, `google_fit_steps_${dateStr}.json`, "application/json");
    log(`📥 JSON файл активности для Google Fit скачан!`);
});

// 3. Copy Summary to Clipboard
document.getElementById("btnCopyClipboard").addEventListener("click", async () => {
    const dateStr = new Date().toLocaleDateString('ru-RU');
    const summary = `📊 Отчет активности (${dateStr}):\n` +
                    `👟 Шаги: ${currentData.steps.toLocaleString('ru-RU')}\n` +
                    `📍 Дистанция: ${currentData.dist.toFixed(2)} км\n` +
                    `🔥 Калории: ${Math.round(currentData.kcal)} ккал\n` +
                    `⏱️ Время в движении: ${Math.floor(currentData.sec / 60)} мин\n` +
                    `🎯 Выполнение цели: ${Math.round((currentData.steps / currentData.goal) * 100)}%`;

    try {
        await navigator.clipboard.writeText(summary);
        alert("Сводка скопирована в буфер обмена!\n\n" + summary);
        log("📋 Сводка скопирована в буфер обмена");
    } catch (e) {
        log("Ошибка копирования в буфер");
    }
});

function downloadFile(content, fileName, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
