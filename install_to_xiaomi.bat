@echo off
chcp 65001 >nul
echo =======================================================
echo  ESP32 Pedometer Sync - Установка APK на телефон Xiaomi
echo =======================================================
echo.

set ADB="D:\Android\SDKs\platform-tools\adb.exe"
set APK="D:\Sources\Giit\Mercury\android_native_app\app\build\outputs\apk\debug\app-debug.apk"

if not exist %ADB% (
    echo [ОШИБКА] ADB не найден по пути: %ADB%
    pause
    exit /b 1
)

if not exist %APK% (
    echo [ОШИБКА] APK файл не найден: %APK%
    pause
    exit /b 1
)

echo Проверка подключенных устройств через ADB...
%ADB% devices -l
echo.

echo Установка APK на устройство...
%ADB% install -r %APK%

if %ERRORLEVEL% equ 0 (
    echo.
    echo =======================================================
    echo  [УСПЕХ] Приложение успешно установлено на телефон!
    echo =======================================================
    echo Запуск приложения...
    %ADB% shell am start -n com.pedometer.companion/.MainActivity
) else (
    echo.
    echo [ВНИМАНИЕ] Установка не удалась.
    echo Пожалуйста, проверьте на телефоне Xiaomi:
    echo 1. 'Настройки' -^> 'Расширенные настройки' -^> 'Для разработчиков'
    echo 2. Включите 'Отладка по USB'
    echo 3. Включите 'Установка через USB' (Обязательно для Xiaomi!)
    echo 4. Подтвердите запрос на экране телефона 'Разрешить отладку с этого компьютера'
)
echo.
pause
