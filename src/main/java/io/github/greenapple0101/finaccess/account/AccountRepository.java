// [이 파일의 역할: Account 저장소 계약]
// interface는 구현 방법이 아니라 사용할 기능의 계약을 표현하는 Java 문법입니다.
// extends JpaRepository<Account, UUID>는 기본 저장·조회 메서드를 상속합니다.
// 첫 타입 인자 Account는 엔티티, 두 번째 UUID는 식별자 타입입니다.
// 이 인터페이스를 직접 new로 생성할 수는 없습니다.
// Spring Data JPA가 저장소 인터페이스를 찾아 프록시와 기본 구현을 구성해 빈으로 제공합니다.
// 그래서 Service의 생성자에서 이 타입을 요구하면 구현 객체를 주입받을 수 있습니다.
//
// save: 신규 객체 영속화 또는 기존 상태 병합. 즉시 DB commit과 같은 뜻은 아닙니다.
// findById: Optional로 한 행 조회 결과를 표현합니다.
// saveAndFlush: SQL 반영까지 요청하지만 전체 트랜잭션 commit은 별개입니다.
// 이 저장소 자체는 HTTP 상태 코드나 로그인 사용자의 접근 권한을 판단하지 않습니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
