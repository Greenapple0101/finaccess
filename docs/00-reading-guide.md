# 코드 읽기가 처음이라면

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
아직 입출금 코드는 없고 초기 0원 잔액의 저장만 다룹니다.

계좌 저장 모델 다음에는 [계좌 개설 API](12-open-account.md)를 읽어보세요. Service → Controller 순서입니다.
