@echo off
echo ===== Trade Agent 前端开发服务器 =====
echo 清理构建缓存...
rmdir /s /q node_modules\.vite 2>nul
rmdir /s /q dist 2>nul
echo 启动 Vite 开发服务器...
npx vite --host 0.0.0.0 --port 5173 --force
