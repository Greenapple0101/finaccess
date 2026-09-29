// [검증된 로그인 신원을 업무 DB의 사용자와 연결합니다]
// 토큰의 iss + sub → app_users 조회 → 우리 앱의 사용자 ID와 회사 ID 순서입니다.
// 요청 본문의 requesterId, actorId나 사용자가 작성한 신원 헤더는 읽지 않습니다.
package io.github.greenapple0101.finaccess.security;

import java.util.UUID;
import io.github.greenapple0101.finaccess.user.AppUser;
import io.github.greenapple0101.finaccess.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentAppUser {
    private final AppUserRepository userRepository;

    public CurrentAppUser(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserIdentity resolve(Jwt jwt) {
        // 이 메서드에는 Spring Security 검증을 통과한 Jwt만 Controller에서 전달합니다.
        AppUser user = userRepository.findByIdentityIssuerAndIdentitySubject(
                        jwt.getIssuer().toString(), jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not provisioned in FinAccess"));
        // 회사 ID는 트랜잭션 안에서 읽고 값만 전달합니다. JPA 객체를 응답에 노출하지 않습니다.
        return new UserIdentity(user.getId(), user.getCompany().getId());
    }

    public record UserIdentity(UUID userId, UUID companyId) {
    }
}
