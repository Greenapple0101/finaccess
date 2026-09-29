// [진짜 서명한 JWT를 HTTP 헤더로 보내 보안 검사소를 시험합니다]
// jwt() 시험 도우미로 인증을 생략하지 않습니다. 임시 RSA 키로 서명하고 실제 JwtDecoder를 통과합니다.
// 임시 웹 서버는 공개 키만 제공합니다. Keycloak이나 DB가 없어도 토큰 검증 정책을 확인할 수 있습니다.
package io.github.greenapple0101.finaccess.security;

import jakarta.servlet.DispatcherType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.github.greenapple0101.finaccess.hello.HelloController;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MeController.class, HelloController.class, JwtSecurityTest.PolicyProbe.class})
@Import({SecurityConfig.class, JwtSecurityTest.PolicyProbe.class})
class JwtSecurityTest {
    private static final String ISSUER = "https://issuer.example/realms/finlab";
    private static final RSAKey SIGNING_KEY;
    private static final HttpServer JWK_SERVER;
    static {
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWK_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWK_SERVER.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWK_SERVER.start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.issuer-uri", () -> ISSUER);
        registry.add("app.security.jwk-set-uri", () -> "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/jwks");
        registry.add("app.security.audience", () -> "finaccess-api");
    }

    @AfterAll
    static void stopPublicKeyServer() { JWK_SERVER.stop(0); }

    @Autowired MockMvc mockMvc;

    @Test
    void publicHelloWorksButIdentityHeadersDoNotAuthenticateProtectedApi() throws Exception {
        mockMvc.perform(get("/api/hello")).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").header("X-User-Id", "admin").header("X-Role", "admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalErrorDispatchIsAllowedButDirectErrorRequestIsDenied() throws Exception {
        // Tomcat이 오류 응답을 만드는 내부 재처리는 허용하지만, 외부 요청으로 /error를 열지는 않습니다.
        mockMvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        })).andExpect(status().isOk());
        mockMvc.perform(get("/error").header(HttpHeaders.AUTHORIZATION, bearer(claims -> {})))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedAccessTokenReturnsIdentityWithoutLeakingToken() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(claims -> {})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value("user-123"))
                .andExpect(jsonPath("$.issuer").value(ISSUER)).andExpect(jsonPath("$.roles[0]").value("user"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "missing-audience", "expired", "future", "subject", "expiration"})
    void rejectsInvalidClaimsBeforeController(String invalid) throws Exception {
        String token = bearer(claims -> {
            switch (invalid) {
                case "issuer" -> claims.issuer("https://other.example");
                case "audience" -> claims.audience("finadmin-api");
                case "missing-audience" -> claims.audience((String) null);
                case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(300)));
                case "future" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(300)));
                case "subject" -> claims.subject(null);
                case "expiration" -> claims.expirationTime(null);
            }
        });
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void signatureFromDifferentPrivateKeyIsRejected() throws Exception {
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(defaultClaims().build(), attackerKey)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateCompanyButThisClientsAdminCan() throws Exception {
        mockMvc.perform(post("/api/companies").header(HttpHeaders.AUTHORIZATION, bearer(claims -> {})))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/companies").header(HttpHeaders.AUTHORIZATION,
                        bearer(claims -> claims.claim("resource_access", roles("finaccess-api", List.of("admin"))))))
                .andExpect(status().isOk());
    }

    @Test
    void approverCanReviewButUserCannot() throws Exception {
        mockMvc.perform(post("/api/transfers/demo/approve").header(HttpHeaders.AUTHORIZATION, bearer(claims -> {})))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers/demo/approve").header(HttpHeaders.AUTHORIZATION,
                        bearer(claims -> claims.claim("resource_access", roles("finaccess-api", List.of("approver"))))))
                .andExpect(status().isOk());
    }

    @Test
    void anotherClientsAdminAndRealmAdminDoNotGrantOurAdminRole() throws Exception {
        String token = bearer(claims -> claims
                .claim("resource_access", roles("finadmin-api", List.of("admin")))
                .claim("realm_access", Map.of("roles", List.of("admin"))));
        mockMvc.perform(post("/api/companies").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void malformedRoleStructuresNeverGrantAdmin() throws Exception {
        for (Object malformed : List.of("admin", Map.of("finaccess-api", "admin"), roles("finaccess-api", "admin"),
                roles("finaccess-api", List.of(123, Map.of("role", "admin")))) ) {
            String token = bearer(claims -> claims.claim("resource_access", malformed));
            mockMvc.perform(post("/api/companies").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        }
    }

    private static Map<String, Object> roles(String client, Object values) {
        return Map.of(client, Map.of("roles", values));
    }

    private static JWTClaimsSet.Builder defaultClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject("user-123").audience("finaccess-api")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("resource_access", roles("finaccess-api", List.of("user")));
    }

    private static String bearer(Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        JWTClaimsSet.Builder claims = defaultClaims();
        customize.accept(claims);
        return "Bearer " + sign(claims.build(), SIGNING_KEY);
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    // 역할 규칙만 확인할 간단한 시험 전용 응답입니다. 실제 DB/API 흐름은 TransferApiTest 등이 따로 검사합니다.
    @RestController
    static class PolicyProbe {
        @GetMapping("/error")
        Map<String, String> errorProbe() { return Map.of("result", "error-dispatch"); }
        @PostMapping({"/api/companies", "/api/transfers/demo/approve"})
        Map<String, String> accepted() { return Map.of("result", "accepted"); }
    }
}
