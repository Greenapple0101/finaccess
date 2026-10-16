# FinAccess 코드를 읽는 순서

[문서 목차](../README.md) · 기준: 2026-10-15 · 현재 실행 대상: `oauth-lab/`

**FinAccess는 화면을 제공하고, USER 역할로 호출하는 API 하나를 제공합니다.** 화면에서 Keycloak으로 로그인한 다음 API7에 Access Token을 보내며, API7과 FinAccess가 각각 JWT·USER 역할을 검사합니다.

## 먼저 펼칠 파일

| 순서 | 파일 | 하는 일 |
|---|---|---|
| 1 | [FinAccessApplication.java](../../oauth-lab/src/main/java/io/github/greenapple0101/finaccess/oauth/FinAccessApplication.java) | main 메서드로 Spring 실행 |
| 2 | [application.properties](../../oauth-lab/src/main/resources/application.properties) | 18100 포트, issuer·JWKS 설정 |
| 3 | [index.html](../../oauth-lab/src/main/resources/static/index.html) | 로그인·갱신·API 호출 버튼과 결과 영역 |
| 4 | [app.js](../../oauth-lab/src/main/resources/static/app.js) | PKCE 로그인 시작, 토큰 교환, API7 호출 |
| 5 | [SecurityConfig.java](../../oauth-lab/src/main/java/io/github/greenapple0101/finaccess/oauth/SecurityConfig.java) | 토큰 검증·USER 접근 규칙 |
| 6 | [UserController.java](../../oauth-lab/src/main/java/io/github/greenapple0101/finaccess/oauth/UserController.java) | 검증된 사용자 번호·역할 응답 |

앱은 `oauth-lab/src/main`의 Java 파일 3개와 정적 화면 파일로 구성합니다. Entity·Repository·JPA·업무 DB는 사용하지 않습니다. 시작 명령과 인증 서버 설정은 [공통 RUNBOOK](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RUNBOOK.md)에 있습니다.

## 브라우저에서 버튼을 누르면

`app.js`의 `authorize()`는 무작위 `state`와 PKCE `verifier`를 만듭니다. verifier를 SHA-256으로 계산한 challenge를 Keycloak에 보냅니다. 비밀번호 입력 화면은 Keycloak이 제공하며, 이 JavaScript가 비밀번호를 받는 구조가 아닙니다.

`callback()`은 Keycloak에서 돌아온 응답이 자신이 시작한 요청인지 state·시간·issuer로 확인합니다. 받은 code를 처음 만든 verifier와 함께 `exchange()`로 전달합니다. Keycloak은 이를 확인하고 Access Token을 발급합니다. PKCE는 가로챈 code만으로 토큰 교환하기 어렵게 하는 장치이며, 전송 내용을 암호화하는 TLS와는 다릅니다.

`callApi()`는 아래 주소에 `Authorization: Bearer ...`를 붙여 보냅니다.

```text
화면: http://localhost:18100/
API:  http://localhost:18090/oauth-access/api/user/me
```

화면과 API 포트가 다릅니다. 브라우저가 먼저 OPTIONS로 CORS 허용 여부를 확인하면 API7이 응답하고, 실제 GET에 JWT·USER 검사를 적용합니다. API7은 `/oauth-access`를 떼고 FinAccess의 `/api/user/me`로 같은 Bearer 토큰을 전달합니다.

## 토큰 저장과 버튼 의미

| 항목 | 실제 동작 |
|---|---|
| Access·Refresh Token | JavaScript 메모리의 tokens에 보관. 새로고침하면 사라짐 |
| state·verifier | 로그인 왕복 동안 sessionStorage의 oauth-pending에 임시 보관 |
| 토큰 갱신 | 버튼을 눌렀을 때 Refresh Token 교환, 자동 갱신 아님 |
| 토큰 지우기 | 페이지 메모리만 지움. Keycloak 세션 로그아웃·기존 JWT 폐기 아님 |
| 토큰 없이 호출 | Authorization 없이 요청해 401 관찰 |
| 결과 영역 | 상태·응답·경유 헤더 표시. Access Token 원문은 표시하지 않음 |

현재 요청 scope는 demo.read이고 openid를 요청하지 않습니다. `exchange()`도 예상과 달리 ID Token이 들어오면 오류로 표시합니다. 이 앱은 OIDC ID Token을 로그인 결과로 처리하지 않습니다.

## Spring 안에서는

`securityFilterChain()`이 먼저 GET 화면 파일을 허용하고 GET `/api/user/me`에 `hasRole("USER")`를 요구합니다. 나머지는 거절합니다. 인증 세션을 저장하지 않고 요청의 Bearer 토큰을 검증합니다.

`jwtDecoder()`가 공개 키로 서명을 검증하고 issuer, audience에 oauth-api 포함 여부, 만료·사용 시작 시간, 필수 exp·sub를 확인합니다. 현재 실습의 시간 오차 허용은 0초입니다.

`readRoles()`는 **검증 후의** JWT에서 resource_access → oauth-api → roles를 읽고 USER·ADMIN을 Spring 내부 이름 ROLE_USER·ROLE_ADMIN으로 바꿉니다. 토큰에 ADMIN만 있으면 USER API는 403입니다. ADMIN이 자동으로 USER 권한까지 갖는 규칙을 만들지 않았습니다.

마지막에 `UserController.me()`가 실행됩니다. Spring이 확인한 인증 객체에서 subject·roles를 읽습니다. 업무 DB를 조회하거나 이체를 처리하지 않습니다.

## 요청 한 건으로 이해하기

| 요청 | API7 | FinAccess |
|---|---|---|
| USER 정상 토큰 | 통과 | USER 재검사 후 200 |
| ADMIN 전용 토큰 | USER 역할 부족 403 | 도달하지 않음 |
| 토큰 없음·변조·만료 | 401 | 도달하지 않음 |
| 직접 18100에 ADMIN 요청 | 경유하지 않음 | USER 역할 부족 403 |

응답의 `X-Finlab-Backend: finaccess`는 Spring 필터 도달 표시입니다. Controller 성공의 보증은 아니므로 상태 코드도 함께 읽습니다. 현재 구성 검증: [검증 자료](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/06-verification.md).
