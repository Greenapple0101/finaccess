# FinAccess · 현재 프로젝트 문서

**2026-10-16 정리 기준: FinAccess는 프런트와 USER API를 제공합니다.** 화면은 로컬 Spring 18100에서 받고 API 요청은 API7 18090을 거쳐 돌아옵니다. 현재 실행 코드는 `oauth-lab/`이며 `main`에도 병합되어 있습니다.

## 처음 읽는 순서

| 문서 | 내용 |
|---|---|
| [FinAccess 코드 읽기](current/01-code-reading.md) | 화면 → PKCE → 토큰 → API7 → Spring USER 검사 |
| [OAuth2 학습 순서](current/02-learning-roadmap.md) | 초보자가 요청 한 건씩 이해할 순서 |
| [전체 범위·유즈케이스](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/01-scope-use-cases.md) | FinAccess·FinAdmin·인증 서버의 책임 |
| [전체 아키텍처·시퀀스](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/02-architecture.md) | 현재 실행 주소와 로그인·API 흐름 |
| [API 명세·권한표](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/03-api-spec.md) | USER·ADMIN, 정상·실패 응답 |
| [Keycloak·LDAP](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/04-keycloak-ldap.md) | 사용자·그룹·department 연결 |
| [API7 설정](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/05-api7-authorization.md) | JWT·ACL·경로·메서드·CORS |
| [검증 결과·캡처](https://github.com/Greenapple0101/finadmin/blob/main/docs/current/06-verification.md) | 실제 USER 200·ADMIN 403 화면과 날짜별 결과 |

## 실행·시연

- [FinAccess 실행 요약](../oauth-lab/README.md)
- [공통 RUNBOOK](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RUNBOOK.md)
- [10/15 API7 연결 검사](https://github.com/Greenapple0101/finadmin/blob/main/oauth-lab/RESULTS-2026-10-15.md)

USER API는 검증된 신원·부서·역할을 응답하며 업무 DB를 조회하지 않습니다. ADMIN은 USER의 상위 Role로 자동 포함되지 않습니다.
