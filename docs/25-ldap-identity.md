# LDAP 직원 계정을 FinAccess 업무 사용자와 연결하기

2026-10-01에 FinLab에 OpenLDAP을 추가했습니다. 실행 설정은 두 앱을 함께 관리하는 [FinAdmin 저장소](https://github.com/Greenapple0101/finadmin)에 있습니다. 전체 설정 이유와 콘솔 실습은 [LDAP 핸즈온 보고서](https://github.com/Greenapple0101/finadmin/blob/main/docs/13-ldap-federation-hands-on.md)를 읽습니다.

## FinAccess 코드는 왜 그대로인가

로그인하는 직원의 명부가 Keycloak 내부 DB에서 OpenLDAP으로 확장됐습니다. 하지만 JWT 발급자는 같은 Keycloak이고 `iss`, `aud`, `sub`, `resource_access.finaccess-api.roles`라는 약속도 유지됩니다.

FinAccess는 LDAP 비밀번호나 그룹을 직접 읽지 않습니다. Keycloak이 서명한 토큰을 기존 Spring Security 설정으로 검증합니다. 이번 확장은 Java 코드와 DB 스키마를 수정하지 않고 기존 신원 연결 구조를 사용했습니다.

```text
OpenLDAP의 직원과 그룹
  → Keycloak User Federation
  → Keycloak 그룹의 finaccess-api Client Role
  → 서명된 JWT
  → API7 Enterprise JWT·역할 검사 및 전달
  → FinAccess JWT·역할 검사
  → CurrentAppUser가 iss + sub로 app_users 조회
  → 회사 소속과 이체 규칙 검사
```

## 로그인 성공과 업무 사용자 등록은 다릅니다

LDAP에서 비밀번호를 맞게 입력했다고 아무 회사 데이터나 볼 수 있는 것은 아닙니다. FinAccess는 검증된 JWT의 `iss + sub`와 업무 사용자 사이의 연결이 필요합니다.

FinAdmin의 `scripts/setup-ldap.py`는 Keycloak에서 실제 LDAP 사용자의 ID를 읽고 다음 실습 연결을 준비합니다. 연결 정보는 FinAccess용 PostgreSQL의 `app_users`에 저장됩니다.

| LDAP 로그인 ID | LDAP 그룹 | FinAccess 역할 | 연결 회사 |
| --- | --- | --- | --- |
| ldap-alice | developers | user | A |
| ldap-bob | approvers | approver | A |
| ldap-admin | admins | user, approver, admin | A |
| ldap-outsider | developers | user | B |

회사 소속은 LDAP 그룹명에서 추측하지 않습니다. 위 표는 명시적으로 준비한 로컬 실습 자료입니다. 이미 다른 회사에 연결된 사용자가 있으면 스크립트가 덮어쓰지 않고 멈춥니다.

LDAP의 `uid`, LDAP의 `entryUUID`, JWT의 `sub`, 업무 DB의 `app_users.id`는 각각 목적이 다릅니다. 이번 구성에서 JWT `sub`는 Keycloak의 가져온 사용자 ID이고, FinAccess는 발급자와 그 ID를 묶어서 신원을 찾습니다.

## 실제 검증한 업무 흐름

FinAdmin 폴더에서 `python3 scripts/verify-ldap.py`로 검사합니다. 이 검사는 로그인뿐 아니라 전용 실습 DB에 새 계좌·모의 이체도 만듭니다.

1. `ldap-admin`이 A회사 출금 계좌와 B회사 입금 계좌를 만듭니다.
2. 검사 스크립트가 새 출금 계좌에 가상 잔액 10,000원을 준비합니다.
3. `ldap-alice`가 1,000원 이체를 요청합니다. 요청자는 JWT로 결정됩니다.
4. 일반 담당자의 승인 시도와 다른 회사 직원의 요청은 403입니다.
5. `ldap-bob`이 승인하고 실행합니다. 승인자·실행자도 JWT로 결정됩니다.
6. 실제 DB의 출금 잔액 9,000원, 입금 잔액 1,000원을 확인합니다.
7. 완료된 거래를 다시 실행하면 409입니다.

LDAP 추가 검증 93개와 원래 내부 계정 HTTP 검증 46개를 통과했습니다. LDAP 숫자는 인증·그룹·신원·HTTP·잔액 확인을 합친 수입니다. 재시작과 설정 재실행 후에도 외부 사용자 연결 ID가 유지됐습니다.

## 접근을 회수할 때의 주의점

LDAP admins 그룹에서 `ldap-admin`을 빼면 새 로그인 토큰에는 그룹에서 물려받던 관리자 역할이 사라집니다. FinAdmin의 관리자 API도 새 토큰에는 403을 반환했습니다.

하지만 이미 발급된 JWT는 발급 당시 역할을 유지합니다. 이번 실험에서도 기존 미만료 토큰으로는 관리자 API가 200이었습니다. LDAP 연동 자체가 기존 토큰을 즉시 폐기하는 기능은 아닙니다.

이번 범위는 OpenLDAP과 로컬 실습용 사용자 등록입니다. 실제 AD, 운영 LDAP TLS, 입·퇴사 자동 처리, 즉시 토큰 폐기 정책은 별도입니다.
