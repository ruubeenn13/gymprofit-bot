@echo off
REM ============================================================
REM Arranque de GymProBot con DOBLE CLIC (sin escribir comandos).
REM Delega todo en run-local.ps1: fija el JDK 21, compila el jar,
REM carga el .env y arranca el bot. Parar: Ctrl+C.
REM La ventana no se cierra sola al terminar, para poder leer
REM cualquier error de compilacion o de arranque.
REM ============================================================

REM Situarse en la raiz del proyecto (este .bat vive en scripts\).
cd /d "%~dp0.."

REM Bypass solo para este proceso: no toca la ExecutionPolicy del sistema.
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\run-local.ps1" %*

pause
