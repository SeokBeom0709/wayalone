# 친구에게 보여 줄 임시 공개 URL을 만듭니다. (Cloudflare Quick Tunnel)
#  - 로컬 개발 DB와 분리된 데모 DB(data/demo-share)를 사용합니다.
#  - 관리자 비밀번호는 실행할 때마다 무작위로 만들어 화면에만 보여 줍니다.
#  - 이 창을 닫으면 URL도 사라집니다. 실행할 때마다 URL이 바뀝니다.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$port = 8082

$jar = Get-ChildItem build\libs\*.jar | Where-Object { $_.Name -notlike '*plain*' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'jar가 없습니다. 먼저 .\gradlew.bat bootJar 를 실행하세요.' }

$adminPw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 16 | ForEach-Object { [char]$_ })
$env:PORT = "$port"
$env:ADMIN_EMAIL = 'admin'
$env:ADMIN_PASSWORD = $adminPw
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:./data/demo-share;AUTO_SERVER=TRUE'
if (Test-Path .env) { } # .env는 사용하지 않습니다(데모는 H2).

Write-Host "서버 시작 중... (포트 $port)"
$app = Start-Process -FilePath $java -ArgumentList '-jar', $jar.FullName -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\wayalone-share.log" -RedirectStandardError "$env:TEMP\wayalone-share.err"

try {
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-WebRequest "http://localhost:$port/" -UseBasicParsing -TimeoutSec 2 | Out-Null; break } catch { Start-Sleep 2 }
    }
    Write-Host "관리자 로그인(본인용): admin / $adminPw"
    Write-Host '터널을 여는 중... 아래에 나오는 https://....trycloudflare.com 주소를 친구에게 보내세요.'
    & 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel --url "http://localhost:$port"
}
finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id -Force }
}
