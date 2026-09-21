package io.github.greenapple0101.finaccess.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// 업무 사용자 저장소입니다. 기본 CRUD 기능은 Spring Data JPA가 제공합니다.
// 아래 findByIdentityIssuerAndIdentitySubject는 메서드 이름에서 조회 조건을 유도합니다.
// Issuer와 Subject가 모두 일치해야 하며, 없을 수 있으므로 Optional로 반환합니다.
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByIdentityIssuerAndIdentitySubject(String identityIssuer, String identitySubject);
}
