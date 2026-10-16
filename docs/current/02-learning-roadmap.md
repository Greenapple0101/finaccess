# OAuth2 중심 학습 순서

[문서 목차](../README.md) · 기준: 2026-10-15

현재 목표는 Spring 업무 기능을 늘리는 것이 아니라 **로그인에서 발급한 토큰이 Gateway와 API에서 어떻게 사용되는지 이해하는 것**입니다. 각 단계에서 한 가지를 설명하고 한 요청으로 확인하면 됩니다.

| 순서 | 이해할 질문 | 읽거나 실행할 것 |
|---|---|---|
| 1 | 화면, 인증 서버, Gateway, API는 각각 무엇을 하나? | [아키텍처](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/02-architecture.md) |
| 2 | 인증과 인가가 왜 다른가? | USER 로그인 성공과 ADMIN API 403 비교 |
| 3 | Code·PKCE·state는 왜 필요한가? | app.js의 authorize → callback → exchange |
| 4 | Access Token과 ID Token은 어떻게 다른가? | [OAuth 메모](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/OAUTH-NOTES.md); 현재 ID Token 미사용 |
| 5 | JWT 내용을 바꿔도 관리자가 되지 않는 이유는? | 복사본 Payload 변조 후 서명 401 확인 |
| 6 | audience와 Role이 무엇이 다른가? | 공통 aud=oauth-api, USER·ADMIN 접근 결과 비교 |
| 7 | API7의 역할 검사는 어디에 있는가? | 제품 콘솔 oauth-access-get / oauth-admin-get의 openid-connect·acl |
| 8 | Gateway가 검사했는데 Spring은 무엇을 하나? | SecurityConfig와 직접 호출 401·403 비교 |
| 9 | 관리자는 화면 없이 어떻게 토큰을 받는가? | Device Grant CLI 시작 → Keycloak 화면 승인 → curl |
| 10 | LDAP 부서 변경은 어디까지 반영되는가? | 새 JWT·기존 JWT department 비교 후 원복 |
| 11 | 400·401·403·500은 어떻게 다른가? | [API 명세](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/03-api-spec.md)와 실제 응답 |

학습과 구현 완료를 구분합니다. Client Credentials·Implicit·SCIM·AI Gateway는 [개념·조사 문서](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/OAUTH-NOTES.md)를 읽되, 이번에 실제 구현·검증한 것으로 말하지 않습니다. 자동화 테스트 소스는 새 최소 실습에 추가하지 않았고 브라우저·curl 관찰 기록으로 동작을 확인했습니다.

위 순서대로 현재 OAuth2 흐름을 한 단계씩 확인합니다. 파일을 읽은 뒤 해당 요청의 응답과 연결해서 설명해 보세요.
