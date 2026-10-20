# Runs the server with the prod profile against the local Docker MySQL (see docker-compose.yml).
# Usage:  powershell -ExecutionPolicy Bypass -File .\run-prod-local.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# Load DB credentials from .env (git-ignored)
Get-Content .env | Where-Object { $_ -match '^\s*[^#=]+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    Set-Item -Path "Env:$($k.Trim())" -Value $v.Trim()
}

$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_URL = "jdbc:mysql://localhost:3306/wayalone?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
if (-not $env:PHONE_PEPPER)   { $env:PHONE_PEPPER   = "local-prod-test-pepper-change-me" }
# LOCAL ONLY: simple admin login. Never use these values on a public server.
if (-not $env:ADMIN_EMAIL)    { $env:ADMIN_EMAIL    = "admin" }
if (-not $env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD = "admin" }
# Local verification only: log provider prints codes to the server log instead of sending SMS.
if (-not $env:SMS_PROVIDER)   { $env:SMS_PROVIDER   = "log" }
if (-not $env:PORT)           { $env:PORT           = "8081" }

.\gradlew.bat bootRun
