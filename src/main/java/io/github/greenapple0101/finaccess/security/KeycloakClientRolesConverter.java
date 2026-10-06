// [Keycloak 역할 이름을 Spring의 권한 이름으로 바꾸는 작은 번역기]
// resource_access.finaccess-api.roles의 admin → ROLE_admin으로 바꿉니다.
// 다른 앱의 admin이나 Keycloak 관리 권한을 우리 앱의 관리자 권한으로 사용하지 않습니다.
package io.github.greenapple0101.finaccess.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakClientRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        // 토큰에 역할이 없거나 자료 모양이 틀리면 역할을 부여하지 않습니다.
        Object resourceAccess = jwt.getClaims().get("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> clients)) {
            return authorities;
        }
        Object clientAccess = clients.get("finaccess-api");
        if (!(clientAccess instanceof Map<?, ?> client)) {
            return authorities;
        }
        Object rolesValue = client.get("roles");
        if (!(rolesValue instanceof Collection<?> roles)) {
            return authorities;
        }
        for (Object roleValue : roles) {
            // 목록에 숫자·객체·빈 역할이 하나라도 섞이면 전체 역할을 거절합니다.
            // 앞에서 admin을 읽었더라도 반환하지 않아 Gateway와 같은 실패 규칙을 지킵니다.
            if (!(roleValue instanceof String role) || role.isBlank()) {
                return List.of();
            }
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return authorities;
    }
}
