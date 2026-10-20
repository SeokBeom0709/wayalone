# WAYALONE

혼자 밥 먹기 좋은 식당을 내 위치 기준으로 찾고, 혼자 가기 애매한 식사·여행은 검증된 사람과 함께하는 서비스입니다.

- 포트폴리오: [docs/PORTFOLIO.md](docs/PORTFOLIO.md)
- 설계 노트: [docs/DESIGN.md](docs/DESIGN.md) / 트러블슈팅: [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- 스택: Java 17 · Spring Boot 4.1 · Spring Data JPA · H2/MySQL · Vanilla JS · 네이버 지도 · 카카오 로컬 API

![내 주변 식당](docs/images/01-nearby-naver.png)

## 실행

```bash
# JDK 17 필요
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
# → http://localhost:8080
```

테스트

```bash
./gradlew test
```

MySQL로 실행하려면 `.env.example`을 `.env`로 복사해 값을 채우고 `docker compose up -d`로 DB를 띄운 뒤, Windows에서는 `run-prod-local.ps1`을 실행합니다.

## 환경변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `KAKAO_REST_KEY` | 주변 식당 기능 사용 시 | 카카오 디벨로퍼스 REST API 키 (카카오맵 사용 설정 ON) |
| `SMS_PROVIDER` | | `log`(기본, 개발용 문자함) / `solapi`(실제 발송) |
| `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SMS_FROM` | `solapi`일 때 | 솔라피 키와 사전 등록된 발신번호 |
| `PHONE_PEPPER` | 운영 필수 | 휴대폰 번호 HMAC 비밀값 |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | 운영 필수 | 시작 시 생성되는 관리자 계정 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ORIGINS` | 운영(`prod`) | MySQL 접속 정보, 허용 출처 |

네이버 지도 Client ID는 `static/js/app.js`의 `NAVER_MAP_CLIENT_ID`에 있으며, 네이버 클라우드 콘솔에서 **Web 서비스 URL**에 접속 주소를 등록해야 합니다. 등록하지 않은 주소에서는 OpenStreetMap으로 자동 전환됩니다.

## 개발용 관리자 계정 (로컬 전용)

`start-server.bat`, `run-prod-local.ps1`로 실행하면 관리자 계정이 `admin` / `admin`으로 만들어집니다. 공개 환경에서는 반드시 `ADMIN_EMAIL`, `ADMIN_PASSWORD`를 다른 값으로 지정하세요.

문자 인증은 개발 모드에서 화면에 인증번호가 표시되며, `#/dev/sms`(개발용 문자함)에서도 확인할 수 있습니다.

## 배포 (Docker)

```bash
docker build -t wayalone .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... \
  -e KAKAO_REST_KEY=... -e PHONE_PEPPER=... \
  -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... wayalone
```
