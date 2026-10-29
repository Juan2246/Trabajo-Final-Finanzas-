@echo off
setlocal
cd /d "%~dp0"
set "JAVA_COMMAND=java"
if defined JAVA_HOME set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"
"%JAVA_COMMAND%" -version >nul 2>&1
if errorlevel 1 (
  echo Instala un JDK 21 y configura JAVA_HOME antes de iniciar.
  echo Guia: https://adoptium.net/installation/
  pause
  exit /b 1
)
echo Preparando Cuenta Clara. La primera vez se descargan las dependencias.
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (
  echo No se pudo compilar. Revisa que uses JDK 21 y tengas conexion a Internet.
  pause
  exit /b 1
)
if not defined DEMO set "DEMO=true"
echo.
echo Abre http://127.0.0.1:8000 en tu navegador.
echo Deja esta ventana abierta. Ctrl+C detiene el servidor.
echo Los datos existentes se conservan.
echo.
"%JAVA_COMMAND%" -jar target\cuenta-clara.jar
pause
