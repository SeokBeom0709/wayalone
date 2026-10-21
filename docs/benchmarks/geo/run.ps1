# 반경 검색 벤치마크를 로컬 Docker MySQL(wayalone-mysql)의 별도 DB(geo_bench)에서 실행합니다.
# 서비스 DB(wayalone)는 건드리지 않고, 끝나면 geo_bench 를 삭제합니다.
# 사전 조건: docker compose up -d db   /   약 5~10분 걸립니다. 실행하는 동안 다른 무거운 작업은 피하세요.
$ErrorActionPreference = 'Stop'
$docker = 'C:\Users\sbs45\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
if (-not (Test-Path $docker)) { $docker = 'docker' }
$sql = Join-Path $PSScriptRoot 'geo-benchmark.sql'

$watch = [System.Diagnostics.Stopwatch]::StartNew()
& $docker cp $sql wayalone-mysql:/tmp/geo-benchmark.sql
# 비밀번호는 컨테이너 안의 환경변수로만 사용하므로 화면과 기록에 남지 않습니다.
& $docker exec wayalone-mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -t < /tmp/geo-benchmark.sql'
& $docker exec wayalone-mysql rm /tmp/geo-benchmark.sql
"전체 소요 시간(초): {0:N0}" -f $watch.Elapsed.TotalSeconds
