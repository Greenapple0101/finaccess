# FinAccess 사용자 화면과 USER API

현재 실습 화면은 FinAccess에 있습니다. FinAccess Spring 하나가 HTML·JavaScript와 USER API를 함께 제공하므로 API7을 경유하지 않습니다. FinAdmin에는 사용자 화면을 두지 않습니다.

## 실행

Java 21, Python 3을 사용합니다. 두 저장소는 같은 부모 폴더에 두고 모두 `oauth-focus` 브랜치를 사용합니다.

```sh
# finadmin 저장소: Keycloak과 옆 FinAccess를 함께 시작
python3 oauth-lab/scripts/start-local.py

# Keycloak이 이미 실행 중이면 finaccess 저장소에서 이것만 실행
python3 oauth-lab/scripts/start-local.py
```

화면: http://localhost:18100/ · API: `GET http://localhost:18100/api/user/me`

처음 실행하면 Spring JAR을 빌드합니다. DB, Docker, JUnit 테스트 코드는 사용하지 않습니다. 상위 폴더의 과거 금융 앱과 별도의 작은 Gradle 프로젝트입니다.

## 요청 흐름

1. 브라우저가 FinAccess에서 `index.html`과 `app.js`를 받습니다.
2. 버튼을 누르면 Keycloak의 `oauth-lab` Realm으로 이동합니다.
3. Authorization Code + PKCE로 Access Token을 받습니다.
4. 브라우저가 같은 주소의 `/api/user/me`에 Bearer 토큰을 붙입니다.
5. Spring이 서명·발급자·대상·시간을 검증하고 USER 역할을 검사합니다.

`alice`는 USER, `admin`은 ADMIN 역할만 갖습니다. ADMIN이 자동으로 USER까지 포함하지 않습니다. 기존 Realm의 소문자 역할은 finadmin의 `oauth-lab/scripts/configure-roles.py`로 변경했습니다. 이전 토큰은 바뀌지 않으므로 다시 발급합니다.

## 화면에서 확인

- alice로 토큰 받기 → **내 USER 권한 확인** → 200
- **토큰 없이 호출** → 401
- USER 역할 없이 같은 API 호출 → 403

```sh
curl -i http://localhost:18100/api/user/me
# 토큰은 로컬 환경 변수로만 준비합니다. 실제 값을 Git에 저장하지 않습니다.
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:18100/api/user/me
```

## 코드 읽는 순서

| 파일 | 하는 일 |
|---|---|
| `src/main/resources/static/index.html` | FinAccess 화면과 버튼 |
| `src/main/resources/static/app.js` | PKCE 코드 교환과 같은 서버의 USER API 호출 |
| `src/main/java/io/github/greenapple0101/finaccess/oauth/FinAccessApplication.java` | 앱 시작 |
| 같은 패키지의 `SecurityConfig.java` | JWT 검증 연결, 검증된 역할을 Spring 권한으로 변환 |
| 같은 패키지의 `UserController.java` | USER 요청에 간단한 JSON 반환 |
| `src/main/resources/application.properties` | 포트, issuer, JWKS, audience |

프런트는 토큰 원문을 메모리에만 보관합니다. 화면은 발급 여부와 API 결과만 표시합니다. **토큰 지우기**는 Keycloak 로그아웃이나 이미 발급한 토큰의 폐기가 아닙니다.

## 이번 단계와 다음 단계

이번 단계는 프런트 소유 위치 이동과 FinAccess 직접 호출입니다. 기존 OAuth2 흐름을 유지하여 `scope=demo.read`를 요청하며 ID Token은 사용하지 않습니다. 새 전체 요구사항의 OIDC·LDAP, FinAdmin 로컬 API와 API7 연결, SCIM은 이 이동으로 완료된 것이 아닙니다.

공식 근거: [Spring JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
