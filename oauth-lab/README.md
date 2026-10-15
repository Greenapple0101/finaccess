# FinAccess 사용자 화면과 USER API

현재 실습 화면은 FinAccess에 있습니다. FinAccess Spring 하나가 HTML·JavaScript와 USER API를 함께 제공합니다. 화면은 18100에서 받지만, 화면이 보내는 USER API 요청은 API7(18090)을 경유합니다. FinAdmin에는 사용자 화면을 두지 않습니다.

## 실행

Java 21, Python 3을 사용합니다. 두 저장소는 같은 부모 폴더에 두고 모두 `oauth-focus` 브랜치를 사용합니다.

```sh
# finadmin 저장소: Keycloak과 옆 FinAccess를 함께 시작
python3 oauth-lab/scripts/start-local.py

# Keycloak이 이미 실행 중이면 finaccess 저장소에서 이것만 실행
python3 oauth-lab/scripts/start-local.py
```

화면: http://localhost:18100/ · API7 진입: `GET http://localhost:18090/oauth-access/api/user/me`

API7의 `oauth-finaccess` 서비스와 `oauth-access-get`·`oauth-access-deny` Route가 필요합니다. FinAdmin의 `oauth-lab` 폴더에서 `python3 scripts/init-api7.py`로 없는 설정만 추가합니다.

처음 실행하면 Spring JAR을 빌드합니다. DB, Docker, JUnit 테스트 코드는 사용하지 않습니다. 상위 폴더의 과거 금융 앱과 별도의 작은 Gradle 프로젝트입니다.

## 요청 흐름

1. 브라우저가 FinAccess에서 `index.html`과 `app.js`를 받습니다.
2. 버튼을 누르면 Keycloak의 `oauth-lab` Realm으로 이동합니다.
3. Authorization Code + PKCE로 Access Token을 받습니다.
4. 브라우저가 API7의 `/oauth-access/api/user/me`에 Bearer 토큰을 붙입니다.
5. API7이 JWT를 검증하고 `USER` 역할을 확인합니다. 통과하면 `/oauth-access` 접두어를 떼고 같은 토큰을 FinAccess에 전달합니다.
6. FinAccess Spring도 서명·발급자·대상·시간과 `USER` 역할을 독립적으로 검사합니다.

화면(18100)과 API7(18090)은 포트가 달라 브라우저의 CORS 사전 확인이 필요합니다. API7은 `http://localhost:18100`의 GET·Authorization 헤더만 허용하고 OPTIONS에 직접 응답합니다. 이 OPTIONS 200은 API 인증 성공이 아니라 본 요청을 보내도 되는지에 대한 응답입니다. 실제 GET에는 JWT·USER 검사가 적용됩니다. CORS는 브라우저 정책이며 curl 차단용 인증 기능이 아닙니다.

`ldap-user`·`alice`는 USER, `ldap-admin`·`admin`은 ADMIN 역할만 갖습니다. LDAP 계정은 부서 속성도 응답에 표시합니다. ADMIN이 자동으로 USER까지 포함하지 않습니다. 기존 Realm의 소문자 역할은 finadmin의 `oauth-lab/scripts/configure-roles.py`로 변경했습니다. 이전 토큰은 바뀌지 않으므로 다시 발급합니다.

## 화면에서 확인

- ldap-user로 토큰 받기 → **내 USER 권한 확인** → 200, `Gateway: api7-enterprise`와 `Backend: finaccess` 표시
- **토큰 없이 호출** → 401
- ADMIN만 가진 계정으로 같은 API 호출 → API7에서 403, `Backend: 없음`

```sh
curl -i http://localhost:18090/oauth-access/api/user/me
# 토큰은 로컬 환경 변수로만 준비합니다. 실제 값을 Git에 저장하지 않습니다.
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:18090/oauth-access/api/user/me
```

## 코드 읽는 순서

| 파일 | 하는 일 |
|---|---|
| `src/main/resources/static/index.html` | FinAccess 화면과 버튼 |
| `src/main/resources/static/app.js` | PKCE 코드 교환과 API7 경유 USER API 호출 |
| `src/main/java/io/github/greenapple0101/finaccess/oauth/FinAccessApplication.java` | 앱 시작 |
| 같은 패키지의 `SecurityConfig.java` | JWT 검증 연결, 검증된 역할을 Spring 권한으로 변환 |
| 같은 패키지의 `UserController.java` | USER 요청에 간단한 JSON 반환 |
| `src/main/resources/application.properties` | 포트, issuer, JWKS, audience |

프런트는 토큰 원문을 메모리에만 보관합니다. 화면은 발급 여부와 API 결과만 표시합니다. **토큰 지우기**는 Keycloak 로그아웃이나 이미 발급한 토큰의 폐기가 아닙니다.

## 현재 범위

OAuth2 Code+PKCE로 `scope=demo.read`를 요청하며 ID Token은 사용하지 않습니다. LDAP 계정 로그인과 FinAccess·FinAdmin의 API7 경유 호출을 확인했습니다. Spring 직접 호출도 검증 비교용으로 남아 있으며, 직접 호출을 네트워크에서 차단한 구성은 아닙니다. 부서는 속성으로 전달하며, 부서별 인가 규칙이나 SCIM 프로비저닝은 구현하지 않았습니다.

Spring의 시계 오차 허용은 로컬 실습에서 0초입니다. JWT 서명·iss·aud·exp·sub를 확인하며 nbf가 있으면 시간 검사에 포함합니다.

[2026-10-15 API7 연결 결과](https://github.com/Greenapple0101/finadmin/blob/oauth-focus/oauth-lab/RESULTS-2026-10-15.md)를 확인할 수 있습니다.

관리자 시연과 부서 동기화 구분은 [FinAdmin 실행 가이드](https://github.com/Greenapple0101/finadmin/blob/oauth-focus/oauth-lab/RUNBOOK.md)를 참고하세요.
