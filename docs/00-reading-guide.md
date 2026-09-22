# 코드 읽기가 처음이라면

## 주석을 읽는 방법

이제 각 Java 파일 첫머리에 그 파일을 사람 말로 풀어 쓴 설명이 있습니다.
그 다음 어노테이션·생성자·메서드 옆 주석을 읽으며 실제 실행문을 확인하세요.
문서에만 설명을 두지 않고 파일 안에서도 실행 흐름과 Java 문법을 따라갈 수 있도록 구성했습니다.

1. FinaccessApplication: main의 문법, Spring 컨테이너, Bean, DI, 자동 설정.
2. HelloController: 가장 작은 요청·응답과 JSON 변환.
3. CompanyController → CompanyService → CompanyRepository → Company: 한 요청의 전체 흐름.
4. AccountController → AccountService → Account: 회사 기능과 비교하며 계좌 개설·입출금 규칙 확인.
5. AppUser: 인증 신원과 회사 소속의 관계. 아직 로그인 검증은 없다는 경계 확인.
6. 테스트: 준비 → 실행 → 검증 순서. MockMvc와 실제 PostgreSQL 테스트의 범위 구분.

필요하면 코드에서 등장하는 메서드 이름으로 검색해 다음 파일로 이동하세요.
예를 들어 Controller의 companyService.register에서 CompanyService의 register로 이동합니다.
긴 패키지 주소는 코드의 위치이고, record는 전달할 데이터 타입입니다. 둘 다 별도의 실행 서버가 아닙니다.

## 이번 설명에서 정확히 구분한 것

- 컴포넌트 탐색과 Entity·Repository 탐색은 동일한 메커니즘이 아닙니다.
- Bean 생성과 앱 초기화는 의존 관계에 따라 진행되며 임의의 고정 순서를 외우지 않습니다.
- 객체 생성, 영속화, flush, commit은 서로 다른 사건입니다.
- @Transactional은 프록시를 통한 호출에서 적용되며 단순한 Java 메서드 호출과 구분합니다.
- 회사가 있다는 사실과 그 회사에 접근할 권한이 있다는 사실은 다릅니다.

적용된 Flyway SQL은 체크섬 보존을 위해 변경하지 않았습니다.
V1·V2는 [SQL 주석 해설](10-sql-commentary.md), V3는 기존 SQL 안의 주석과 [계좌 모델 설명](11-account-model.md)을 읽으면 됩니다.

공식 참고: [Spring 트랜잭션 어노테이션](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)


처음부터 모든 파일을 읽을 필요는 없습니다. 아래 순서로 하나씩 열어보세요.
각 소스 파일의 한국어 주석은 문법, Spring의 역할, 실행 시점을 설명합니다.

## 먼저 알아둘 문법

- `package`: 이 클래스가 속한 이름 공간입니다. 폴더 구조와 연결됩니다.
- `import`: 다른 패키지의 타입을 짧은 이름으로 쓰도록 가져옵니다. 객체를 만드는 코드는 아닙니다.
- `class`: 객체의 구조와 행동을 정의합니다.
- 필드: 객체가 보관하는 값입니다. 메서드: 객체가 수행하는 동작입니다.
- 생성자: new로 객체를 만들 때 실행됩니다. 클래스와 이름이 같고 반환 타입이 없습니다.
- `public`: 외부에서 사용 가능. `private`: 해당 클래스 내부에서 사용.
- `return`: 실행 결과를 호출한 곳으로 돌려줍니다.
- `new`: 객체를 만듭니다. DB 저장과는 별개입니다.
- `@...`: 도구나 프레임워크가 읽어 동작을 구성하는 어노테이션입니다.
- `//`: 한 줄 주석입니다. 실행되지 않으며 설명을 위한 문장입니다.

## 첫 번째: 시작과 가장 작은 응답

1. [FinaccessApplication](../src/main/java/io/github/greenapple0101/finaccess/FinaccessApplication.java)
2. [HelloController](../src/main/java/io/github/greenapple0101/finaccess/hello/HelloController.java)
3. [HelloControllerTest](../src/test/java/io/github/greenapple0101/finaccess/hello/HelloControllerTest.java)

질문: 요청 URL은 어디에 적혀 있나요? 응답 메시지는 어느 줄에서 만들어지나요?

## 두 번째: 회사 등록 요청을 따라가기

1. [CompanyController](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyController.java)의 register
2. [CompanyService](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyService.java)의 register
3. [Company](../src/main/java/io/github/greenapple0101/finaccess/company/Company.java)의 생성자
4. [CompanyRepository](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyRepository.java)

입력 JSON → 요청 DTO → 이름 검사 → 저장 → 결과 DTO → 응답 JSON 순서입니다.
조회는 같은 파일들의 findById를 따라가세요. 없는 회사의 예외가 어떻게 404가 되는지도 보세요.

## 세 번째: 회사와 사용자의 관계

1. [AppUser](../src/main/java/io/github/greenapple0101/finaccess/user/AppUser.java)
2. [AppUserRepository](../src/main/java/io/github/greenapple0101/finaccess/user/AppUserRepository.java)
3. [관계 저장 테스트](../src/test/java/io/github/greenapple0101/finaccess/user/AppUserPersistenceTest.java)

질문: 회사 이름 대신 company_id를 저장하는 이유는 무엇인가요?
이 코드는 소속 관계를 저장할 뿐, 아직 Keycloak 로그인이나 접근 권한을 검사하지 않습니다.

## 네 번째: 설정과 실행 기반

1. [build.gradle](../build.gradle): 필요한 라이브러리
2. [compose.yaml](../compose.yaml): DB 실행 환경
3. [application.properties](../src/main/resources/application.properties): 공통 설정
4. [application-local.properties](../src/main/resources/application-local.properties): 로컬 설정
5. [SQL 주석 해설](10-sql-commentary.md): 테이블과 제약

Gradle Wrapper가 생성한 gradlew와 wrapper 파일은 지금 읽지 않아도 됩니다.
실행 도구 자체의 내부 구현이므로 학습용 주석을 추가하지 않았습니다.

## 다섯 번째: 테스트 읽기

[통합 테스트](../src/test/java/io/github/greenapple0101/finaccess/FinaccessApplicationTests.java)에서
테스트 메서드 이름과 그 위의 설명을 먼저 읽으세요.
준비 → 동작 실행 → 기대 결과 검증의 세 부분을 찾으면 됩니다.

`assertThat(...).isEqualTo(...)`는 두 값이 같아야 한다는 검증,
`assertThatThrownBy(...)`는 예외 발생을 기대하는 검증입니다.
테스트 코드의 데이터는 별도 컨테이너 DB에서 사용되며 개발용 DB와 분리됩니다.

## 이어서: 계좌 모델

[계좌 모델을 만든 순서](11-account-model.md)에 따라 V3 SQL → Account → AccountRepository → 테스트를 읽어보세요.
이 문서에서는 초기 0원 잔액의 저장부터 다룹니다. 입출금은 아래 13 문서로 이어집니다.

계좌 저장 모델 다음에는 [계좌 개설 API](12-open-account.md)를 읽어보세요. Service → Controller 순서입니다.

다음은 [입출금 규칙](13-balance-rules.md)입니다. Account 메서드 → 단위 테스트 → DB 변경 감지 테스트 순서입니다.

다음은 [이체 상태를 만든 순서](14-transfer-status.md)입니다. TransferStatus의 값 목록 → transitionTo → 테스트의 상태 표 순서로 읽으세요.

이어서 [이체 요청 객체](15-transfer-request-model.md)를 읽으세요. 필드 → 생성자 검사 → getter → 테스트 순서입니다. 아직 DB 저장은 하지 않습니다.

다음 [이체 요청 저장](16-transfer-request-persistence.md)에서는 V4 SQL → TransferRequest의 JPA 어노테이션 → Repository → 저장 테스트 순으로 연결합니다.

[이체 요청 서비스](17-transfer-request-service.md)는 생성자 주입 → request의 조회·검증·저장 → 결과 record → 서비스 테스트 순으로 읽으세요.
