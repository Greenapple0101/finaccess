// [테스트 파일을 처음 읽는 방법]
// 이 파일은 운영 요청을 처리하는 코드가 아니라, 기존 코드가 약속을 지키는지 자동 확인하는 코드입니다.
// ./gradlew test를 실행하면 JUnit이 @Test 또는 @ParameterizedTest 메서드를 찾아 실행합니다.
// 각 테스트는 준비(객체·데이터) → 실행(메서드·요청) → 검증(기대한 값)의 순서로 읽으세요.
// 테스트 메서드가 파일에 적힌 순서대로 실행된다고 가정하면 안 됩니다.
//
// assertThat(실제값).isEqualTo(기대값): 값이 같아야 성공합니다.
// isZero/isNotNull/isEmpty 등은 같은 방식으로 기대 조건을 표현합니다.
// assertThatThrownBy(() -> 호출): 람다를 실행할 때 예외가 발생하는지 확인합니다.
// 예외를 기대한 테스트에서 그 예외가 나면 테스트는 성공할 수 있습니다.
// 테스트 실패는 기대 결과와 실제 결과가 다르다는 뜻이며, 전체 프로그램이 잘못됐다는 뜻만은 아닙니다.
//
// [HTTP 테스트 문법]
// MockMvc는 실제 네트워크 포트를 열지 않고 Spring MVC 요청 처리를 실행합니다.
// perform(post/get(...))는 요청 실행, andExpect(...)는 응답 검증입니다.
// contentType은 보내는 본문의 형식, content는 보내는 문자열입니다.
// 응답 contentTypeCompatibleWith는 응답의 미디어 타입이 JSON 등에 맞는지 확인합니다.
// jsonPath("$.name")에서 $는 JSON의 최상위 객체, .name은 그 객체의 name 필드입니다.
// andReturn은 결과를 가져오며 getResponse/getContentAsString으로 본문을 읽을 수 있습니다.
// ObjectMapper.readValue는 JSON을 지정한 Java 타입으로 변환합니다.
// ClassName.class는 그 타입 정보를 넘기는 표현입니다. new로 객체를 만드는 것과 다릅니다.
// throws Exception은 예외가 밖으로 전달될 수 있다는 메서드 선언이며 예외를 무시하는 명령은 아닙니다.
// try/finally는 검증 중 실패하더라도 finally의 데이터 정리를 실행하기 위해 사용합니다.
//
// @WebMvcTest(HelloController.class)는 이 Controller 중심의 웹 계층만 구성합니다.
// 전체 DB를 포함한 @SpringBootTest와 범위가 다릅니다.
// 그래도 URL 매핑과 JSON 변환은 실행되므로 hello()를 직접 호출하는 테스트보다 넓게 확인합니다.

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
