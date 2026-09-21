package io.github.greenapple0101.finaccess.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// [읽기 5] interface는 사용할 수 있는 기능의 계약입니다.
// extends JpaRepository<Company, UUID>는 기본 저장·조회 기능을 상속받습니다.
// 꺾쇠 안의 Company는 관리 대상 타입, UUID는 식별자 타입입니다.
// Spring Data JPA가 구현체를 제공하므로 save/findById의 SQL을 여기서 직접 쓰지 않습니다.
public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
