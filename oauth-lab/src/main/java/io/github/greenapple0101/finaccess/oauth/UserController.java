package io.github.greenapple0101.finaccess.oauth;

import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 업무 DB 없이 'USER 권한이 있는 요청이 여기까지 왔다'는 사실만 보여 주는 API입니다.
@RestController
public class UserController {
    @GetMapping("/api/user/me")
    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        // authentication은 Spring Security가 검증한 결과입니다.
        // 요청 본문이나 X-User 같은 임의 헤더에서 권한을 가져오지 않습니다.
        return Map.of(
            "application", "FinAccess",
            "message", "USER 권한으로 FinAccess API에 접근했습니다.",
            "authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        );
    }
}
