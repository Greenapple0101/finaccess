// [앱 사용자를 저장하고 찾는 통로]
// 사용자 ID로 찾는 기본 조회와, 로그인 신원의 두 연결값으로 찾는 조회를 제공합니다.
// JpaRepository에서 save·findById 등을 물려받고, 아래에는 연결값 조회를 추가했습니다.
// Service가 저장소를 호출하면 Spring Data JPA가 준비한 구현 객체가 DB 조회를 처리합니다.
// 이 저장소는 사용자 정보를 찾을 뿐 비밀번호나 토큰이 올바른지 검증하지는 않습니다.

package io.github.greenapple0101.finaccess.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// AppUser는 저장할 데이터, UUID는 우리 앱 사용자 ID의 타입입니다.
// interface에는 사용할 기능을 적습니다. 실제 구현은 Spring Data JPA가 준비하므로 여기에는 내용이 짧습니다.
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    // 메서드 이름으로 조회 조건을 설명합니다.
    // findBy는 "찾아라", IdentityIssuer와 IdentitySubject는 AppUser의 필드 이름,
    // And는 "두 조건이 모두 맞아야 한다"는 뜻입니다.
    // 예: 발급자 X와 사용자 "123"을 주면, 그 두 값을 함께 가진 사용자만 찾습니다.
    // Spring Data JPA가 이름을 해석하고 입력값을 조회 조건에 연결합니다. SQL 문자열을 직접 조립하지 않습니다.
    // Optional은 일치하는 사용자가 없을 수도 있다는 뜻입니다. 없을 때의 처리는 호출한 서비스가 결정합니다.
    Optional<AppUser> findByIdentityIssuerAndIdentitySubject(String identityIssuer, String identitySubject);
}
