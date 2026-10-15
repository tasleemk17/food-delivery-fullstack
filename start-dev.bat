@echo off
rem Development mode with hot reload on Windows: the backend services run in
rem Docker, the two React apps run with "npm run dev".
rem   start-dev.bat           Docker backend services + customer app + admin panel
rem   start-dev.bat stripe    the same, plus the Stripe CLI webhook forwarder
rem Stop: close the opened windows, then run "docker compose down".
rem
rem If you only want to USE the app (no code editing), run "docker compose up -d --build"
rem instead: it also starts the React apps in Docker on the same ports.

cd /d "%~dp0"

echo Starting Docker services (backend, gateway, order-service, postgres)...
rem The React containers use ports 5173 and 5174, which "npm run dev" needs, so stop them
docker compose stop smallapp admin >nul 2>&1
docker compose up -d backend gateway order-service postgres
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
