# FinAccess 프런트 이동 확인

> **초기 단계의 과거 기록입니다.** 아래 USER API 직접 호출 설명은 당시 상태입니다. 현재 양쪽 API의 API7 경유 구성은 [10/15 결과](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RESULTS-2026-10-15.md)와 [현재 문서](../docs/README.md)를 읽습니다.

확인일: 2026-10-14, 로컬 macOS / Java 21 / Spring Boot 4.1.1 / Keycloak 26.7.4.

| 요청 | 예상 | 실제 |
|---|---|---|
| FinAccess 화면 `GET /` | 200, FinAccess 화면 | 200 |
| 토큰 없이 `GET /api/user/me` | 401 | 401 |
| 실제 Keycloak USER 토큰으로 직접 호출 | 200 | 200 |
| 실제 Keycloak ADMIN 전용 토큰으로 직접 호출 | 403 | 403 |
| USER 토큰의 역할을 ADMIN으로 변조하고 서명은 유지 | 401 | 401 |
| 브라우저에서 alice 인증 후 USER API 버튼 클릭 | 200 | 200 |

USER와 ADMIN 토큰은 각각 Authorization Code + PKCE로 새로 발급했습니다. CLI 검증은 curl 요청으로 수행했고, 토큰·비밀번호·쿠키 원문은 이 기록에 포함하지 않습니다. 토큰 없는 응답에도 `X-Finlab-Backend: finaccess`가 있었습니다.

`bootJar` 빌드와 프런트 JavaScript 문법 검사에 성공했습니다. JUnit이나 자동화 테스트 파일을 추가하거나 실행한 결과가 아닙니다. API7 경유·새 FinAdmin 최소 앱·LDAP·OIDC를 이번 이동에서 검증한 것으로 해석하지 않습니다.
