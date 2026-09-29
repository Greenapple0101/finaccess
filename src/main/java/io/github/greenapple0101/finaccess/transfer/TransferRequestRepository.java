package io.github.greenapple0101.finaccess.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// interface는 사용 가능한 기능의 계약입니다. 직접 new로 만들지 않습니다.
// Spring Data JPA가 이 인터페이스의 구현을 제공하고 Spring Bean으로 등록합니다.
// <TransferRequest, UUID>는 저장 대상 타입과 그 기본키 타입입니다.
// extends로 save, findById 등의 기본 기능을 물려받아 별도의 SQL 구현 없이 사용할 수 있습니다.
// saveAndFlush는 SQL 반영까지 요청합니다. flush와 트랜잭션 commit은 다릅니다.
// 저장소는 요청자의 로그인 여부나 회사 접근 권한을 검사하지 않습니다.
public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
    // 트랜잭션 종료까지 요청 행에 쓰기 잠금을 잡습니다. 동시 승인·반려가 같은 옛 상태를 쓰지 않게 합니다.
    // JPQL은 테이블명이 아니라 엔티티와 필드 이름을 사용합니다. :id에는 매개변수가 바인딩됩니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferRequest t where t.id = :id")
    Optional<TransferRequest> findLockedById(@Param("id") UUID id);
}
