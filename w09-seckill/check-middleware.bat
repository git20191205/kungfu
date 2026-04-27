@echo off
chcp 65001 >nul
echo ========================================
echo   秒杀系统 - 中间件状态检查
echo ========================================
echo.

set ERROR_COUNT=0

echo [1/4] 检查 MySQL (3306)...
netstat -an | findstr "3306" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo   ✓ MySQL 正在运行
) else (
    echo   ✗ MySQL 未运行
    set /a ERROR_COUNT+=1
)
echo.

echo [2/4] 检查 Redis (6379)...
netstat -an | findstr "6379" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo   ✓ Redis 正在运行
) else (
    echo   ✗ Redis 未运行
    set /a ERROR_COUNT+=1
)
echo.

echo [3/4] 检查 Kafka (9092)...
netstat -an | findstr "9092" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo   ✓ Kafka 正在运行
) else (
    echo   ✗ Kafka 未运行
    set /a ERROR_COUNT+=1
)
echo.

echo [4/4] 检查 Nacos (8848)...
netstat -an | findstr "8848" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo   ✓ Nacos 端口正在监听
    echo   → 正在检查 Nacos 健康状态...
    curl -s http://localhost:8848/nacos/v1/console/health/readiness >nul 2>&1
    if %errorlevel% equ 0 (
        echo   ✓ Nacos 已就绪，可以启动微服务
    ) else (
        echo   ⚠ Nacos 正在启动中，请等待 30-60 秒
        set /a ERROR_COUNT+=1
    )
) else (
    echo   ✗ Nacos 未运行
    echo   → 请执行: cd D:\nacos\bin ^&^& startup.cmd -m standalone
    set /a ERROR_COUNT+=1
)
echo.

echo ========================================
if %ERROR_COUNT% equ 0 (
    echo   ✓ 所有中间件已就绪，可以启动微服务！
    echo ========================================
    exit /b 0
) else (
    echo   ✗ 发现 %ERROR_COUNT% 个问题，请先解决
    echo ========================================
    exit /b 1
)
