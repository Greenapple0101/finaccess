package io.github.greenapple0101.finaccess.hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HelloController에 필요한 웹 계층만 준비하는 테스트입니다.
// 실제 DB를 띄우지 않고 URL 매핑·상태 코드·JSON 응답을 확인합니다.
@WebMvcTest(HelloController.class)
class HelloControllerTest {

    // Spring이 준비한 객체를 테스트 필드에 주입합니다.
    @Autowired
    private MockMvc mockMvc;

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // perform은 요청 실행, andExpect는 기대 결과 검증입니다.
    // 200 상태, JSON Content-Type, 메시지 본문을 각각 확인합니다.
    // throws Exception은 테스트 중 발생한 예외를 테스트 실행기로 전달할 수 있다는 선언입니다.
    void returnsGreetingAsJson() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"message":"Hello, FinAccess!"}
                        """));
    }
}
