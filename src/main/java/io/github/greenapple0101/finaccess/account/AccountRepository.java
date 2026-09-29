// [계좌 데이터를 저장하고 찾는 통로]
// 계좌 개설 서비스는 save를, 이체 서비스는 계좌 조회와 잠금 기능을 사용합니다.
// Service → 저장소의 구현 객체 → JPA → DB 순서로 요청이 전달됩니다.
// JpaRepository에서 일반 저장·조회 기능을 물려받고, 아래에는 잠금 조회 기능을 추가했습니다.
//
// 잠금은 같은 계좌를 동시에 바꾸려는 DB 작업 사이에서 차례를 정하는 방법입니다.
// 예를 들어 한 계좌에서 두 요청이 동시에 출금하려고 할 때, 한 요청이 처리 중이면 다른 쪽을 기다리게 합니다.
// 로그인한 사람의 권한을 검사하는 기능과는 다릅니다.

package io.github.greenapple0101.finaccess.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Account는 저장할 데이터 타입, UUID는 계좌 ID 타입입니다.
// interface에는 사용 가능한 기능을 적고, 실제 저장소 객체는 Spring Data JPA가 준비합니다.
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // 아래 조회를 DB의 쓰기 잠금과 함께 실행하게 합니다. 서비스의 트랜잭션 안에서 호출해야 합니다.
    // 잠금은 그 트랜잭션이 끝날 때 풀립니다. 일반 조회까지 전부 막는 잠금은 아닙니다.
    // 두 계좌를 잠글 때 순서가 엇갈리면 서로 기다리는 상황(교착)이 생길 수 있습니다.
    // 그래서 TransferWorkflowService는 계좌 ID를 비교해 항상 같은 순서로 잠급니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // "Account 객체 중 ID가 같은 것 하나를 찾아라"라는 조회식입니다.
    // 이 문법은 테이블 이름을 쓰는 SQL이 아니라 Java 엔티티와 필드 이름을 쓰는 JPQL입니다.
    // @Param("id")로 받은 값이 :id 자리에 연결됩니다. ID를 SQL 문자열에 직접 이어 붙이지 않습니다.
    @Query("select a from Account a where a.id = :id")
    // Optional은 계좌가 있을 수도, 없을 수도 있다는 뜻입니다.
    // 이름만 특별하게 붙여서 잠기는 것이 아니라 위의 @Lock이 잠금 동작을 지정합니다.
    Optional<Account> findLockedById(@Param("id") UUID id);
}
