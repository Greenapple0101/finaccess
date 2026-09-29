// [지금 API가 누구의 토큰을 인정했는지 확인하는 학습용 API]
// 비밀번호나 토큰 원문은 돌려주지 않고, 검증된 식별값과 이 앱의 역할만 보여줍니다.
package io.github.greenapple0101.finaccess.security;

import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    @GetMapping("/api/me")
    public Identity me(JwtAuthenticationToken authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                // Spring이 붙이는 FACTOR_BEARER 같은 인증 방식 표시는 앱 역할 목록에서 제외합니다.
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted().toList();
        return new Identity(authentication.getToken().getSubject(),
                authentication.getToken().getIssuer().toString(), roles);
    }

    public record Identity(String sub, String issuer, List<String> roles) {
    }
}
