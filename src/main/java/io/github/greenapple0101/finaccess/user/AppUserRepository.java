package io.github.greenapple0101.finaccess.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByIdentityIssuerAndIdentitySubject(String identityIssuer, String identitySubject);
}
