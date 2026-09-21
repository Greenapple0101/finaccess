# 09. 로그인 계정과 업무 사용자, 회사 소속

## 이번 단계의 질문

Keycloak에서 로그인한 사람이 우리 서비스의 어느 회사 소속인지 어떻게 알까요?

Keycloak은 로그인 계정을 관리하고, FinAccess는 업무 사용자와 소속 회사를 관리합니다.
이번 단계에서는 연결할 데이터를 저장할 모델만 만들었습니다.
Keycloak 연동, 사용자 등록 API, 회사별 접근 차단은 아직 구현하지 않았습니다.

## 첫 모델의 범위

한 사용자는 한 회사에 소속됩니다. 한 회사에는 여러 사용자가 있을 수 있습니다.
여러 회사 겸직이 필요해지면 별도 membership 테이블을 도입하는 모델 변경을 검토합니다.
현재는 같은 로그인 계정을 두 회사에 중복 등록할 수 없습니다.

```text
companies (회사 하나)
     ↑ company_id 외래키
app_users (여러 업무 사용자)
```

## app_users 테이블

| 컬럼 | 의미 |
| --- | --- |
| id | FinAccess 내부 사용자 UUID |
| company_id | 소속 회사의 id, 필수 외래키 |
| identity_issuer | 인증 발급자, 향후 검증된 토큰의 iss |
| identity_subject | 해당 발급자가 식별하는 사용자, 향후 검증된 토큰의 sub |
| created_at | DB가 기록하는 생성 시각 |

issuer와 subject의 조합에 UNIQUE 제약을 둡니다.
이메일과 표시 이름은 안정적인 사용자 식별자가 아니므로 연결키로 사용하지 않습니다.
서로 다른 issuer의 동일 subject는 다른 사용자입니다. 대소문자나 URL을 임의로 정규화하지 않습니다.
비밀번호와 토큰 원문은 저장하지 않습니다.
issuer 512자, subject 255자는 현재 저장 모델의 길이 제한입니다.

향후 요청 본문으로 사용자가 주장하는 회사·issuer·subject를 신뢰하지 않습니다.
신뢰할 발급자에 대한 토큰 검증 후 얻은 신원으로 서버가 업무 사용자와 소속을 찾도록 연결해야 합니다.
공개 사용자 등록 API는 이번에 만들지 않았습니다.

## 새 어노테이션

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "company_id", nullable = false, updatable = false)
private Company company;
```

- ManyToOne: 여러 AppUser가 하나의 Company를 참조합니다.
- LAZY: 관계를 지연 로딩하도록 요청합니다. 관련 회사의 상세 정보가 필요할 때 조회할 수 있습니다.
- optional=false: 회사 참조가 필수라는 JPA 매핑 정보입니다.
- JoinColumn: 관계를 저장하는 DB 컬럼은 company_id입니다.
- updatable=false: JPA의 UPDATE에서 소속 컬럼을 제외합니다. 현재 소속 변경 기능이 없다는 범위에 맞췄습니다.

LAZY 관계는 트랜잭션 밖에서 접근할 때 로딩 문제가 발생할 수 있습니다.
향후 Service 트랜잭션 안에서 필요한 값을 읽고 DTO를 만들어 반환합니다.
updatable=false는 DB 수준 권한 설정이 아니며 직접 SQL 변경까지 막지는 않습니다.

회사에서 사용자로 향하는 컬렉션과 cascade 설정은 추가하지 않았습니다.
사용자는 이미 저장된 회사에 연결합니다. 사용자를 지워도 회사가 함께 삭제되지 않습니다.
DB 외래키는 사용자가 참조 중인 회사의 삭제도 기본적으로 막습니다.

## Repository

```java
Optional<AppUser> findByIdentityIssuerAndIdentitySubject(
    String identityIssuer, String identitySubject);
```

Spring Data JPA가 메서드 이름을 해석해 두 필드가 모두 일치하는 사용자를 조회합니다.
조회 결과가 없다면 Optional.empty()입니다.

## 마이그레이션과 검증

V1을 수정하지 않고 V2__create_app_users.sql을 추가했습니다.
앱 시작 시 Flyway가 V2를 적용하고 JPA가 매핑을 확인합니다.

```bash
./gradlew test
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

테스트에서는 다음을 확인합니다.

- 여러 사용자의 동일 회사 소속 저장·조회
- DB에서 읽은 회사명과 생성 시각
- 같은 issuer·subject의 다른 회사 중복 등록 거부
- 다른 issuer의 같은 subject 구분
- 존재하지 않는 회사 ID의 외래키 위반 거부

## 생각해 볼 질문

- Keycloak 사용자 ID와 FinAccess 내부 사용자 ID를 왜 구분할까요?
- 로그인에 성공했다고 해서 회사 소속까지 자동으로 정해질까요?
- 외래키와 회사별 접근 권한 검사는 어떤 차이가 있을까요?

공식 근거: https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability
