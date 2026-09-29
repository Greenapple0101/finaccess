// [이 파일은 무엇을 하나요?]
// 이체 요청을 DB에 저장하거나 찾는 통로입니다. Service가 이 통로를 사용합니다.
// 기본 저장·조회는 JpaRepository에서 물려받고, 아래에는 잠그면서 조회하는 기능을 추가했습니다.
// 이 통로는 데이터를 찾습니다. 로그인 확인이나 회사 접근 권한 판단은 하지 않습니다.
package io.github.greenapple0101.finaccess.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// interface는 "이런 기능을 쓸 수 있다"는 약속입니다. Spring Data JPA가 실제 동작을 준비해 줍니다.
// <TransferRequest, UUID>는 "저장할 대상은 이체 요청, 고유 번호의 타입은 UUID"라는 뜻입니다.
// extends 덕분에 save(저장), findById(ID로 조회) 같은 기본 기능을 그대로 사용할 수 있습니다.
public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
    // 같은 요청을 동시에 승인하거나 실행하면 서로 옛 상태를 보고 처리할 수 있습니다.
    // 그래서 Service의 트랜잭션 안에서 요청 한 행에 쓰기 잠금을 얻고 읽습니다.
    // 다른 트랜잭션의 수정·충돌하는 잠금은 기다리며, 이 잠금은 우리 트랜잭션 종료 시 풀립니다.
    // DB 전체나 모든 일반 읽기를 막는다는 뜻은 아닙니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 이 문자열은 JPQL입니다. SQL과 비슷하지만 테이블 대신 Java 객체 이름과 필드를 씁니다.
    // 뜻은 "TransferRequest 중 id가 전달받은 값과 같은 것을 찾아라"입니다.
    // @Param("id")가 메서드의 id 값을 :id 자리에 안전하게 연결합니다.
    @Query("select t from TransferRequest t where t.id = :id")
    // Optional은 결과가 없을 수도 있음을 나타냅니다. Service가 없을 때의 오류 처리를 정합니다.
    Optional<TransferRequest> findLockedById(@Param("id") UUID id);
}
