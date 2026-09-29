// [요청이 Controller에 도착하기 전의 검사소]
// 1. Bearer 토큰을 읽고 2. 서명과 토큰 내용을 검증한 뒤 3. 주소에 필요한 역할을 확인합니다.
// 검증 실패는 401, 로그인은 확인했지만 역할이 부족하면 403입니다.
// Gateway를 통과했다는 말이나 X-User-Id 같은 임의 헤더를 신뢰하지 않습니다.
package io.github.greenapple0101.finaccess.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration // Spring이 아래 @Bean 메서드로 보안 도구를 준비합니다.
@EnableWebSecurity // 웹 요청의 보안 검사에 필요한 Spring 구성을 연결합니다.
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakClientRolesConverter());

        http
                // 쿠키로 자동 전송되는 로그인 세션을 사용하지 않고, 매 요청의 Bearer 토큰을 검사합니다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 이 API는 쿠키 인증을 받지 않으므로 CSRF 검사를 끕니다. 쿠키 로그인으로 바꾸면 재검토해야 합니다.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // 잘못된 JSON 등의 400 응답을 만드는 서버 내부 오류 처리는 통과시킵니다.
                        // ERROR는 서버가 정하는 재처리 종류입니다. 외부의 GET /error를 공개하는 규칙이 아닙니다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hello").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/companies", "/api/companies/*/accounts").hasRole("admin")
                        .requestMatchers(HttpMethod.GET, "/api/companies/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/transfers").hasAnyRole("user", "admin")
                        .requestMatchers(HttpMethod.POST, "/api/transfers/*/approve", "/api/transfers/*/reject", "/api/transfers/*/execute")
                        .hasAnyRole("approver", "admin")
                        // 새 API를 만들 때 규칙을 빠뜨리면 자동으로 공개되지 않게 닫아 둡니다.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.issuer-uri}") String issuer,
                          @Value("${app.security.jwk-set-uri}") String jwkSetUri,
                          @Value("${app.security.audience}") String audience) {
        // Keycloak의 공개 키 목록(JWKS)으로 RSA 서명을 확인합니다. 개인 키는 Keycloak만 보관합니다.
        // 공개 키를 받는 주소와 토큰의 발급자 주소를 분리하여 Docker 내부 통신도 가능하게 합니다.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            // aud뿐 아니라 사용자 식별값과 만료 시간도 반드시 있는 액세스 토큰을 사용합니다.
            boolean allowed = jwt.getAudience() != null && jwt.getAudience().contains(audience)
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getExpiresAt() != null;
            if (allowed) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required token claims are missing or invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, requiredClaims));
        return decoder;
    }
}
