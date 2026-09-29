// [이 파일의 역할] /api/hello로 요청하면 정해진 인사말을 JSON으로 보내는지 확인합니다.
// JSON은 {"message":"인사말"}처럼 이름과 값을 묶어 주고받는 글 형식입니다.
// 이 시험은 DB 없이 Controller의 요청 처리에 필요한 부분만 준비합니다.
// 읽는 순서: MockMvc 도구 준비 → GET 요청 보내기 → 응답 코드/형식/내용 확인.
// 테스트 실행 도구인 JUnit이 @Test가 붙은 메서드를 찾아 실행합니다.

package io.github.greenapple0101.finaccess.hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HelloController의 요청 처리에 필요한 Spring 설정만 준비하라는 표시입니다.
@WebMvcTest(HelloController.class)
class HelloControllerTest {

    // Spring이 준비한 MockMvc를 아래 변수에 넣어 줍니다. 직접 new로 만들 필요가 없습니다.
    @Autowired
    private MockMvc mockMvc;

    // GET은 정보를 달라는 요청입니다. /api/hello로 보낸 뒤 세 가지를 검사합니다.
    // 200(정상 처리)인지, 응답이 JSON인지, message의 내용이 맞는지 확인합니다.
    // throws Exception은 오류가 나면 시험 실행 도구에 알릴 수 있다는 선언입니다.
    @Test
    void returnsGreetingAsJson() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"message":"Hello, FinAccess!"}
                        """));
    }
}
