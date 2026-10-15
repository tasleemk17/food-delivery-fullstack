@echo off
rem Starts the whole project for local development on Windows.
rem   start-dev.bat           Docker services + customer app + admin panel
rem   start-dev.bat stripe    the same, plus the Stripe CLI webhook forwarder
rem Stop: close the opened windows, then run "docker compose down".

cd /d "%~dp0"

echo Starting Docker services (backend, gateway, order-service, postgres)...
docker compose up -d
if errorlevel 1 (
  echo.
  echo Docker failed to start. Is Docker Desktop running?
  pause
  exit /b 1
)

if not exist "smallapp\node_modules" (
  echo Installing customer app dependencies...
  call npm install --prefix smallapp
)
if not exist "admin\node_modules" (
  echo Installing admin panel dependencies...
  call npm install --prefix admin
)

start "Customer app - http://localhost:5173" cmd /k "cd /d %~dp0smallapp && npm run dev"
start "Admin panel - http://localhost:5174" cmd /k "cd /d %~dp0admin && npm run dev"

if /i "%~1"=="stripe" (
  start "Stripe webhook" cmd /k "stripe listen --events checkout.session.completed,checkout.session.expired --forward-to localhost:8090/api/order/webhook"
)

echo.
echo Customer app: http://localhost:5173
echo Admin panel:  http://localhost:5174
echo Gateway:      http://localhost:8090
echo.
