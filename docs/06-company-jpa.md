# 06. Company 객체와 companies 테이블 연결하기

## 이번 단계의 질문

Java 객체를 저장하면 어떻게 DB의 행이 되고, 다시 객체로 돌아올까요?

이번 범위는 회사 엔티티와 저장소입니다. 회사 등록 API와 사용자 소속 연결은 이후 단계입니다.

## 세 기술의 관계

- JPA: Java 객체와 관계형 DB를 연결하는 표준입니다.
- Hibernate: 이번 프로젝트에서 JPA를 구현하는 라이브러리입니다.
- Spring Data JPA: Repository 인터페이스로 기본 저장·조회 기능을 편리하게 제공합니다.

`spring-boot-starter-data-jpa`가 JDBC 기반 연결 구성도 포함하므로 기존 JDBC 스타터를 대체합니다.
기존 JdbcTemplate 테스트는 계속 사용할 수 있습니다.

## Company 읽기

| 코드 | 의미 |
| --- | --- |
| `@Entity` | JPA가 관리할 객체 |
| `@Table(name = "companies")` | 연결할 테이블 |
| `@Id` | 기본키 필드 |
| `@GeneratedValue(strategy = GenerationType.UUID)` | 영속화할 때 JPA 구현체가 UUID 생성 |
| `@Column` | 컬럼 이름과 매핑 속성 |
| `protected Company()` | DB에서 객체를 복원할 때 JPA가 사용할 기본 생성자 |
| `new Company(name)` | 앱에서 이름을 검증하며 회사 객체 생성 |

회사 이름은 필수이고 최대 100자입니다. 자바 객체를 만들 때 이를 확인합니다.
DB의 기존 NOT NULL·CHECK·길이 제한도 유지합니다.
공백 검사는 Java의 isBlank가 SQL trim보다 더 넓은 종류의 공백을 검사할 수 있습니다.

id는 생성 직후 null이며 저장 시 UUID가 부여됩니다.
이 방식에서는 Spring Data JPA가 id가 null인 객체를 새 객체로 판단할 수 있습니다.

`createdAt`은 PostgreSQL의 `created_at`에 연결됩니다.
`insertable=false, updatable=false`는 JPA가 이 컬럼을 INSERT·UPDATE에 포함하지 않도록 합니다.
DB가 기존 DEFAULT CURRENT_TIMESTAMP로 시각을 채웁니다.
**현재 매핑에서는 저장 직후 객체에 DB 생성 시각이 자동 반영되지는 않습니다.**
DB에서 다시 읽거나 refresh한 뒤 확인할 수 있습니다.

## Repository 읽기

```java
public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
```

`Company`는 다루는 엔티티, `UUID`는 식별자의 타입입니다.
Spring Data가 실행 중 구현체를 구성하므로 직접 클래스를 만들지 않아도 됩니다.
`save`, `findById` 등의 메서드를 사용할 수 있습니다.
`findById`는 회사가 없는 경우를 표현하기 위해 Optional을 반환합니다.

## 저장과 조회 테스트

1. 회사 객체를 생성합니다.
2. `saveAndFlush`로 저장하고 SQL을 DB에 반영합니다.
3. `EntityManager.clear()`로 영속성 컨텍스트를 비웁니다.
4. `findById`로 다시 조회합니다.
5. 이름과 DB가 만든 생성 시각을 확인합니다.

영속성 컨텍스트는 JPA가 관리하는 객체를 보관하는 공간입니다.
비우지 않으면 같은 id 조회 시 메모리의 객체를 반환할 수 있습니다.
flush는 SQL 반영이며 commit과 다릅니다. 테스트의 변경은 종료 시 롤백됩니다.

## Flyway와 JPA의 역할 분리

- Flyway: 버전별 SQL로 테이블 구조 변경
- `spring.jpa.hibernate.ddl-auto=validate`: 시작 시 엔티티와 테이블의 매핑 호환성 검사
- `spring.jpa.open-in-view=false`: 웹 응답 처리까지 영속성 컨텍스트를 열어두지 않음

validate는 모든 CHECK 제약이나 업무 규칙을 검증하는 기능은 아닙니다.
V1 SQL은 수정하지 않았습니다. 기존 테이블 구조에 객체를 맞췄습니다.

## 직접 확인

Docker를 실행한 뒤:

```bash
./gradlew test
```

테스트는 별도 PostgreSQL에서 수행되어 개발용 DB에 회사 데이터를 남기지 않습니다.

## 생각해 볼 질문

- 인터페이스만 작성했는데 save를 호출할 수 있는 이유는 무엇일까요?
- flush와 commit은 어떻게 다를까요?
- 테스트에서 clear 없이 findById를 호출하면 어떤 부분을 확인하지 못할 수 있을까요?

공식 문서: https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
