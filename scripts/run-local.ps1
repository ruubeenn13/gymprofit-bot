# ============================================================
# Arranca GymProBot en LOCAL: fija JDK 21, compila, carga el .env y ejecuta el jar.
# Uso:
#   .\scripts\run-local.ps1              → compila y arranca
#   .\scripts\run-local.ps1 -SkipBuild   → arranca sin recompilar (si el jar ya está)
# Parar el bot: Ctrl+C.
# ============================================================
param([switch]$SkipBuild)

$ErrorActionPreference = "Stop"

# Situarse en la raíz del proyecto (este script vive en scripts/).
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# JDK 21 (el java por defecto del sistema es 8; hay que forzar el 21 para compilar/arrancar).
$env:JAVA_HOME = "$HOME\.jdks\ms-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Compilar el fat-jar salvo que se pida saltarlo.
if (-not $SkipBuild) {
    Write-Host "==> Compilando el jar..." -ForegroundColor Cyan
    .\mvnw.cmd -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Fallo al compilar (revisa el error arriba)" }
}

# Cargar las variables del .env al proceso (sin interpretar el shell: seguro con los '&' de la URL).
if (-not (Test-Path ".env")) { throw "Falta el archivo .env (cópialo de .env.example y rellénalo)" }
Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*?)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

Write-Host "==> Arrancando GymProBot (Ctrl+C para parar)..." -ForegroundColor Green
java -jar target/gymprofit-bot.jar
