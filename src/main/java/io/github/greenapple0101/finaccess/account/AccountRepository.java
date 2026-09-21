package io.github.greenapple0101.finaccess.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// [만든 순서 3] Account 객체를 저장하고 UUID로 조회하는 저장소입니다.
// 기본 save/findById 기능은 Spring Data JPA가 제공합니다.
// 현재는 잠금·이체·회사 소속 권한 검사가 없는 기본 저장소입니다.
public interface AccountRepository extends JpaRepository<Account, UUID> {
}
