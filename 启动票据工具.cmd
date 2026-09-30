@echo off
cd /d "%~dp0"
if not exist node_modules\vite\bin\vite.js (
  echo Please run npm install first. See README.md.
  pause
  exit /b 1
)
if not exist dist\index.html (
  call npm.cmd run build
  if errorlevel 1 exit /b 1
)
call npm.cmd run preview -- --port 4173 --strictPort --open
