> **과거 학습 기록 — 현재 실행 가이드가 아닙니다.**
> 현재 OAuth2 최소 구성은 [문서 목차](README.md)에서 확인합니다. 이 문서의 금융·ITSM·DB·이전 인증 설정, 주소, 역할 이름과 캡처는 작성 당시의 자료입니다.
> 본문의 “현재”·“최신”도 당시 시점을 뜻합니다. 과거 검사 개수를 이번 검증으로 합산하지 않습니다. 현재 실행 코드는 두 저장소의 `oauth-lab/`이며 USER·ADMIN API 모두 API7을 경유합니다.

# [과거 기록] 처음 읽는 FinAccess 코드

각 파일의 첫 주석에서 **무슨 일을 하는지**를 먼저 읽고, 실행 순서에 나온 메서드를 따라가세요.
괄호와 어노테이션을 한 번에 모두 외울 필요는 없습니다. "무엇을 받고, 무엇을 확인하고, 무엇을 돌려주는가"를 찾으면 됩니다.

## 1. 폴더가 여러 겹인 이유

`io/github/greenapple0101/finaccess`는 코드의 주소를 나타냅니다. 서버가 여러 개라는 뜻이 아닙니다.
그 아래 `company`는 회사, `account`는 계좌, `transfer`는 이체 관련 파일을 모은 폴더입니다.
한 폴더를 읽기 전에 그 안의 모든 파일을 외울 필요도 없습니다.

회사 등록을 예로 들면 네 종류의 코드가 일을 나눕니다.

| 코드 | 쉬운 설명 | 회사 등록에서 하는 일 |
| --- | --- | --- |
| Controller | 웹 요청을 받는 입구 | JSON으로 받은 회사 이름을 서비스에 전달 |
| Service | 필요한 작업의 순서를 정하는 곳 | 회사 객체를 만들고 저장을 요청 |
| Company | 회사 한 곳의 정보와 규칙 | 이름이 비어 있거나 너무 긴지 검사 |
| Repository | 저장·조회 기능을 사용하는 통로 | 회사 정보를 DB에 저장 |

실제로는 Controller → Service → Company 생성 → Repository 순서로 진행됩니다.
Repository 안쪽의 JPA·Hibernate가 객체를 SQL 작업으로 연결합니다.
Controller가 항상 같은 순서로 모든 종류의 파일을 방문해야 한다는 Java 규칙은 아닙니다.

## 2. 처음에는 세 파일만 읽으세요

1. [FinaccessApplication](../src/main/java/io/github/greenapple0101/finaccess/FinaccessApplication.java): 앱을 켜는 시작점. `main`에서 `run`을 호출합니다.
2. [HelloController](../src/main/java/io/github/greenapple0101/finaccess/hello/HelloController.java): `/api/hello` 요청에 인사말을 돌려줍니다.
3. [HelloControllerTest](../src/test/java/io/github/greenapple0101/finaccess/hello/HelloControllerTest.java): 응답이 약속한 값인지 자동 확인합니다.

앱을 켜는 과정과 요청을 처리하는 과정은 다릅니다. 요청할 때마다 main부터 다시 실행하지 않습니다.

## 3. 회사에서 계좌로 넓혀 읽기

- **회사 등록·조회:** [Controller](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyController.java)의 `register` → [Service](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyService.java)의 `register` → [Company](../src/main/java/io/github/greenapple0101/finaccess/company/Company.java)의 생성자 → [Repository](../src/main/java/io/github/greenapple0101/finaccess/company/CompanyRepository.java).
- **회사 소속 사용자:** [AppUser](../src/main/java/io/github/greenapple0101/finaccess/user/AppUser.java). "이 사람은 어느 회사 소속인가"를 저장합니다. 로그인 기능 자체는 아닙니다.
- **계좌 개설:** [AccountController](../src/main/java/io/github/greenapple0101/finaccess/account/AccountController.java) → [AccountService](../src/main/java/io/github/greenapple0101/finaccess/account/AccountService.java) → [Account](../src/main/java/io/github/greenapple0101/finaccess/account/Account.java).
- **입출금 계산:** Account의 `deposit`과 `withdraw`, 이어서 [AccountTest](../src/test/java/io/github/greenapple0101/finaccess/account/AccountTest.java).

메서드 이름으로 검색해 다음 파일을 찾으면 됩니다. 예를 들어 `accountService.open`을 봤다면 AccountService에서 `open`을 찾습니다.

## 4. 이체는 숫자 예시를 붙여 읽기

A 계좌에 1,500원, B 계좌에 200원이 있고 A에서 B로 1,000원을 보낸다고 가정합니다.

1. **요청 생성:** REQUESTED가 됩니다. 아직 A는 1,500원, B는 200원입니다.
2. **다른 사용자의 승인:** APPROVED가 됩니다. 여전히 잔액은 그대로입니다.
3. **실행 성공:** A는 500원, B는 1,200원, 상태는 COMPLETED가 됩니다.
4. **실행 중 실패:** 두 계좌의 잔액은 실행 전 상태로 되돌리고 요청은 APPROVED로 남습니다.

[TransferController](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferController.java)에서 경로를 찾고,
[TransferRequestService](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequestService.java)의 `request`,
[TransferWorkflowService](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferWorkflowService.java)의 `approve`·`reject`·`execute`를 따라가세요.

[TransferRequest](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferRequest.java)는 이체 내용과 진행 상태를 보관합니다.
[TransferStatus](../src/main/java/io/github/greenapple0101/finaccess/transfer/TransferStatus.java)는 상태를 어떤 순서로 바꿀 수 있는지 정합니다.
[TransferApiTest](../src/test/java/io/github/greenapple0101/finaccess/transfer/TransferApiTest.java)는 정상 흐름뿐 아니라 실패·동시 실행도 확인합니다.

입력한 사용자 ID가 실제 로그인한 본인인지 확인하는 기능은 아직 없습니다. 회사 소속 비교와 본인 인증은 서로 다른 검사입니다.

## 5. 자주 나오는 말

| 말 | 이 프로젝트에서 뜻하는 것 |
| --- | --- |
| 클래스 / 객체 | 클래스는 정보와 동작의 정의, 객체는 그 정의로 만든 실제 대상입니다. Company 하나로 여러 회사 객체를 만들 수 있습니다. |
| 필드 / 메서드 | 객체가 보관하는 값 / 객체가 수행하는 동작. 잔액은 필드, 출금은 메서드입니다. |
| 생성자 / new | 객체를 만들 때 실행하는 준비 코드 / 객체를 만드는 표현. 객체 생성만으로 DB에 저장되지는 않습니다. |
| package / import | 코드의 소속 주소 / 다른 주소의 타입을 짧은 이름으로 부르기 위한 선언입니다. |
| private / public | 해당 클래스 안에서 사용하는 범위 / 다른 코드에서도 접근 가능한 범위입니다. |
| final | 한 번 정한 변수에 다른 값을 다시 대입하지 못하게 합니다. 참조한 객체의 내부까지 전부 고정하는 뜻은 아닙니다. |
| 어노테이션 `@...` | Spring이나 JPA 같은 도구가 읽는 표시입니다. `@Entity`는 DB와 연결할 대상이라는 표시입니다. |
| Bean / DI | Spring이 관리하는 객체 / 필요한 객체를 밖에서 전달받는 방식. Service가 Repository를 생성자로 받는 것이 예입니다. |
| 프록시 | 실제 객체 앞에서 추가 일을 맡는 객체. 서비스 호출 전 트랜잭션을 시작하고 호출 후 저장을 확정할 수 있습니다. |
| DTO / record | 필요한 데이터만 전달하려는 객체 / 데이터 묶음을 간단히 선언하는 Java 문법. 서로 같은 단어는 아닙니다. |
| UUID | 회사·계좌 등을 구별하는 ID 형식. 예: `123e4567-e89b-12d3-a456-426614174000`. 실제 은행 계좌번호는 아닙니다. |
| Optional | 조회한 값이 있을 수도, 없을 수도 있다는 표현. `orElseThrow`는 없을 때 예외를 던집니다. |
| 예외 / throw | 정상 처리를 계속할 수 없다는 신호 / 그 신호를 보내는 표현. 잔액 부족이면 출금을 중단합니다. |
| JPA / Hibernate | Java 객체와 DB를 연결하는 규칙 / 그 규칙에 따라 실제 일을 하는 구현 도구입니다. |
| 변경 감지 | JPA가 관리 중인 객체의 값이 달라졌는지 확인해 DB 변경에 반영하는 기능입니다. |
| 트랜잭션 | 여러 DB 작업을 함께 성공시키거나 실패 시 함께 취소하는 범위입니다. 출금·입금·완료를 하나로 묶습니다. |
| flush / commit / rollback | 변경 SQL을 DB로 보내기 / 최종 확정하기 / 취소하기. flush 후에도 commit 전에는 롤백될 수 있습니다. |
| 잠금 | 같은 데이터를 바꾸려는 다른 작업과 순서를 맞추는 방법. 여기서는 요청과 계좌 행의 쓰기 잠금을 사용합니다. |
| LAZY | 연결된 회사 정보 등을 처음부터 전부 읽지 않고 필요할 때 읽도록 요청하는 설정입니다. 필요한 시점에 DB 조회가 추가될 수 있습니다. |

같은 용어를 각 파일에서 길게 반복하는 대신 이 표를 함께 사용하세요.

## 6. 테스트를 읽는 순서

**준비 → 실행 → 확인**으로 나누면 됩니다. `assertThat(실제값).isEqualTo(기대값)`은 둘이 같아야 성공한다는 뜻입니다.
`assertThatThrownBy(() -> 동작)`은 그 동작이 예외를 던지는지 확인합니다. 예외를 기대한 테스트는 예외가 나야 성공할 수 있습니다.

MockMvc는 실제 네트워크 포트를 열지 않고 웹 요청 처리를 확인합니다.
Testcontainers는 Docker로 테스트 전용 DB를 준비합니다. 개발 DB의 잔액을 바꾸는 테스트가 아닙니다.
테스트에 `@Transactional`이 있으면 기본적으로 끝날 때 롤백됩니다.
실제 저장 확정을 확인하는 서비스·API 테스트는 테스트 전체를 트랜잭션으로 감싸지 않고, 끝난 뒤 데이터를 직접 정리합니다.

## 7. 설정과 SQL 읽기

[build.gradle](../build.gradle)은 필요한 도구, [compose.yaml](../compose.yaml)은 DB 실행 환경,
[공통 설정](../src/main/resources/application.properties)과 [local 설정](../src/main/resources/application-local.properties)은 앱의 접속·동작 설정입니다.
Gradle이 제공하는 `gradlew` 같은 실행 도구의 내부 코드는 지금 읽지 않아도 됩니다.

SQL은 [DB 변경 파일 쉬운 해설](22-database-reading-guide.md)을 먼저 읽으세요.
이미 적용한 SQL은 주석만 바꿔도 Flyway가 다른 파일로 판단할 수 있으므로 원본은 유지했습니다.
과거 단계 문서는 그 당시 구현 범위를 설명하고, 현재 동작은 소스 주석과 [요청 API](18-transfer-api.md)·[승인/반려](19-transfer-review.md)·[내부 이체](20-internal-transfer.md) 문서를 기준으로 읽으면 됩니다.

[읽기 쉽게 리팩토링한 이유와 전후 비교](23-readable-refactoring.md)에서 현재 이름과 코드를 확인할 수 있습니다.
