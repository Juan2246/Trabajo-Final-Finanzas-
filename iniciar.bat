@echo off
setlocal
cd /d "%~dp0"
python --version >nul 2>&1
if errorlevel 1 (
  echo Necesitas instalar Python 3.12 o superior y marcar "Add Python to PATH".
  pause
  exit /b 1
)
python -m pip install -r requirements.txt
if errorlevel 1 (
  echo No se pudieron instalar las zonas horarias. Revisa tu conexion.
  pause
  exit /b 1
)
echo.
echo La app se abrira en http://127.0.0.1:8000
echo Deja esta ventana abierta mientras uses la app.
echo.
python app.py --demo
pause
