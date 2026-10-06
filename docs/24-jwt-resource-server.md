# 24. 토큰을 확인하고, 로그인한 사람으로 업무 처리하기

이전 단계까지는 요청 본문에 `requesterId`나 `actorId`를 적었습니다. 그 값은 작성자가 바꿀 수 있으므로 로그인 증거가 아닙니다. 이제 FinAccess는 **검증된 액세스 토큰**으로 사용자를 확인합니다.

Keycloak 실행·사용자 설정·Gateway와 두 앱 통합 실행은 [FinAdmin 문서](https://github.com/Greenapple0101/finadmin/tree/main/docs)에 있습니다. 이 문서는 FinAccess 코드 안에서 일어나는 일을 설명합니다.

## 1. 요청이 지나가는 순서

1. 프런트가 `Authorization: Bearer 액세스토큰`을 넣어서 요청합니다.
2. API7 Enterprise가 JWT와 경로·메서드별 앱 역할을 검사하고 같은 토큰을 FinAccess에 전달합니다.
3. FinAccess의 Spring Security도 JWT를 직접 검증합니다. Gateway가 보낸 사용자 ID 헤더만 보고 믿지 않습니다.
4. 토큰이 유효하면 이 API에 필요한 역할을 확인합니다.
5. Controller가 검증된 `Jwt`를 받습니다.
6. 이체 업무에서는 `iss + sub`로 우리 DB 사용자를 찾습니다.
7. Service가 회사 소속·자기 승인 금지·상태·잔액을 검사합니다.

Gateway 앞의 검사와 백엔드 안의 검사는 각각 있습니다. 백엔드를 직접 호출해도 잘못된 토큰으로 우회할 수 없습니다. 운영에서는 이 검증에 더해 백엔드에 접근할 수 있는 네트워크도 제한해야 합니다.

## 2. 먼저 만든 파일: SecurityConfig

`src/main/java/io/github/greenapple0101/finaccess/security/SecurityConfig.java`는 보안 도구를 연결합니다.

- `@Configuration`: 설정을 준비하는 클래스입니다.
- `@Bean`: 이 메서드가 만든 도구를 Spring이 관리하고 다른 곳에 연결합니다.
- `SecurityFilterChain`: 요청이 Controller에 도착하기 전에 거치는 보안 검사 순서입니다.
- `JwtDecoder`: 글자로 온 토큰의 서명을 확인하고 내용을 읽는 도구입니다. 단순 Base64 해독과 다릅니다.
- `JwtAuthenticationConverter`: 검증된 토큰을 Spring이 사용하는 로그인 정보로 바꿉니다.

`NimbusJwtDecoder`는 Keycloak의 JWKS(공개 키 목록)를 읽어 RS256 서명을 검증합니다. 개인 키를 백엔드로 복사하지 않습니다. 공개 키는 캐시될 수 있으므로 모든 요청마다 Keycloak에 로그인 확인을 하는 구조는 아닙니다.

기본 시간 검사에는 서버 간 시계 차이를 위한 허용 범위가 있습니다. 테스트에서는 만료·미래 시간을 5분 차이로 만들어 분명하게 거부되는지 확인합니다. 이 앱은 서명 외에 `iss`, 시간(`exp`, `nbf`), 대상(`aud`)을 검사하고 `sub`와 `exp`가 없는 토큰도 거부합니다.

## 3. 주소 세 개를 구분하기

| 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `JWT_ISSUER_URI` | `http://localhost:18080/realms/finlab` | 토큰의 발급자 `iss`가 이 값과 같아야 합니다. |
| `JWT_JWK_SET_URI` | `http://localhost:18080/realms/finlab/protocol/openid-connect/certs` | 서명 확인용 공개 키를 받을 주소입니다. |
| `JWT_AUDIENCE` | `finaccess-api` | 이 API에서 사용하도록 발급된 토큰인지 확인합니다. |

Docker 내부에서 `localhost`는 해당 컨테이너 자신입니다. 따라서 컨테이너에서는 JWKS 주소를 Keycloak의 내부 서비스 주소로 바꿀 수 있습니다. 그렇다고 `iss` 검사까지 끄거나 임의 내부 주소로 바꾸면 안 됩니다. 토큰에 실제로 담기는 발급자 주소와 정확히 맞춰야 합니다.

## 4. 역할은 어디에서 읽을까?

`KeycloakClientRolesConverter`는 다음 위치만 읽습니다.

```json
{
  "resource_access": {
    "finaccess-api": {"roles": ["user", "approver"]}
  }
}
```

이것은 실제 토큰이 아니라 모양을 보여주는 예시입니다. `user`는 `ROLE_user`, `approver`는 `ROLE_approver`로 바뀝니다. `hasRole("approver")`가 `ROLE_approver`를 찾기 때문입니다.

다른 앱인 `finadmin-api`의 `admin`이나 Realm의 `admin`을 FinAccess 관리자 역할로 사용하지 않습니다. 같은 역할 이름이어도 어느 앱의 역할인지 구분해야 합니다. 역할 자료가 없거나 형태가 틀리면 권한을 부여하지 않습니다.

| API | 필요한 역할 및 업무 조건 |
| --- | --- |
| `GET /api/hello` | 백엔드 직접 호출만 공개. Gateway 경유는 user·approver·admin 중 하나 필요 |
| `GET /api/me` | 정상 토큰 + user·approver·admin 중 하나. 토큰 원문 없이 sub·issuer·역할 반환 |
| `POST /api/companies` | `admin` |
| `POST /api/companies/{id}/accounts` | `admin` |
| `GET /api/companies/{id}` | user·approver·admin 중 하나 + 자기 회사의 연결된 사용자. `admin`은 여러 회사 조회 허용 |
| `POST /api/transfers` | `user` 또는 `admin`, 출금 회사 소속 필요 |
| `POST /api/transfers/{id}/approve` | `approver` 또는 `admin`, 출금 회사 소속·자기 승인 금지 |
| `POST /api/transfers/{id}/reject` | `approver` 또는 `admin`, 출금 회사 소속·자기 반려 금지 |
| `POST /api/transfers/{id}/execute` | `approver` 또는 `admin`, 출금 회사 소속·승인 상태·잔액 확인 |

이체 실행에서는 출금 회사 소속을 확인한 다음 승인 상태를 검사합니다. 다른 회사의 결재자에게는 REQUESTED·APPROVED·REJECTED·COMPLETED 모두 403을 반환합니다. 상태에 따라 403과 409를 다르게 보내면 접근 권한이 없는 사람이 상태를 추측할 수 있기 때문입니다. 계좌는 기존처럼 UUID 순서대로 잠그며, 이 검사 전에 잔액을 일반 조회하지 않습니다.

`admin`에게 회사 생성·조회·계좌 개설은 운영 작업으로 허용했습니다. 하지만 이체 업무의 회사 소속과 자기 승인 금지는 `admin`도 우회하지 못합니다. 새 주소는 규칙에 추가하지 않으면 기본 거부합니다. 잘못된 JSON 등의 오류를 처리하는 서버 내부 `ERROR` 재처리만 별도로 허용하여, 원래의 400 응답이 403으로 바뀌지 않게 합니다. 외부에서 직접 `/error`로 요청하면 여전히 거부합니다.

## 5. 토큰의 사용자와 DB 사용자를 연결하기

`CurrentAppUser`는 JWT의 `iss`와 `sub`를 함께 사용합니다.

- `iss`: 이 신원을 발급한 곳입니다.
- `sub`: 그 발급자가 정한 사용자 식별값입니다.
- `app_users.id`: FinAccess 업무 데이터에서 쓰는 별도 ID입니다.

이메일·표시 이름은 바뀔 수 있어서 연결 기준으로 삼지 않습니다. 토큰의 사용자와 일치하는 업무 사용자가 없으면 403입니다. 로그인 성공이 회사 가입 완료를 뜻하지는 않기 때문입니다. 사용자 등록 API는 이번에 추가하지 않았고, 로컬 실습에서는 초기 데이터 준비 단계에서 회사와 사용자를 연결합니다.

`@AuthenticationPrincipal Jwt jwt`는 Spring Security가 검증한 로그인 정보를 Controller에 전달하는 표시입니다. 새 이체의 본문은 다음 세 항목뿐입니다.

```json
{
  "sourceAccountId": "실제 출금 계좌 UUID",
  "destinationAccountId": "실제 입금 계좌 UUID",
  "amountWon": 1000
}
```

요청자 ID는 `currentAppUser.resolve(jwt).userId()`로 찾습니다. 승인·반려·실행은 본문 없이 요청합니다. 빈 객체 `{}`는 허용하지만 `actorId`를 넣으면 400입니다. 이체 생성에 `requesterId`나 정의하지 않은 항목을 넣어도 400입니다. 본문을 통해 다른 사람으로 처리되게 할 수 없습니다.

## 6. 왜 CSRF 검사를 끄고 세션을 안 만들까?

이 백엔드는 브라우저가 자동 전송하는 로그인 쿠키를 인증 수단으로 사용하지 않습니다. 호출자는 매번 Bearer 토큰을 명시적으로 보내야 합니다. 그래서 서버 로그인 세션을 만들지 않고 이 API의 CSRF 검사를 껐습니다. 나중에 쿠키 기반 로그인/BFF를 연결한다면 같은 설정을 그대로 복사하지 말고 CSRF 방식을 다시 정해야 합니다.

## 7. 자동 시험은 무엇을 증명할까?

`JwtSecurityTest`는 임시 RSA 키로 실제 토큰에 서명하고, 임시 JWKS 웹 서버에서 공개 키를 제공합니다. 실제 `Authorization` 헤더 → 실제 decoder → 실제 권한 규칙을 통과합니다.

- 토큰 없음·서명 키 불일치·잘못된 발급자·다른 API 대상·만료·미래 사용 시작·필수값 누락은 401.
- 일반 사용자의 관리자·승인 API 호출은 403.
- 해당 앱의 관리자·결재자 역할은 각 허용 경로에 진입.
- 다른 앱 관리자·Realm 관리자·잘못된 모양의 역할은 권한 상승 불가.
- `X-User-Id` 같은 헤더만으로 인증 불가.

`TransferApiTest`는 검증된 사용자 상황을 Spring 시험 도구로 준비하고, 실제 PostgreSQL에서 회사 격리·사용자 연결·ID 위조 거절·이체·롤백·동시 실행을 확인합니다. 금융 시험에 사용하는 `jwt()`는 서명 검증을 생략하는 도구이므로, 이 시험만으로 JWT 검증을 완료했다고 말하지 않습니다.

```bash
./gradlew test bootJar
```

## 8. 남는 운영 경계

로컬 HTTP 주소는 학습용입니다. 운영에서는 HTTPS, Gateway만 백엔드에 접근할 네트워크, 키 회전, 비밀 관리, 로그 마스킹과 운영 계정 관리가 필요합니다. JWT를 공개 키로 검증할 때는 Keycloak에서 역할을 바꾸거나 로그아웃해도 이미 발급한 토큰이 즉시 무효가 된다고 보장되지 않습니다. 짧은 토큰 수명 및 필요 시 별도 회수 정책을 설계해야 합니다.

참고: [Spring JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Keycloak 서버 관리 문서](https://www.keycloak.org/docs/latest/server_admin/).
