// [이 파일이 하는 일] GET /api/hello로 온 요청에 인사말을 보내는 가장 작은 예제입니다.
// 요청이 들어옴 → hello 실행 → HelloResponse 생성 → JSON 응답 순서입니다.
// JSON은 {"message":"Hello, FinAccess!"}처럼 이름과 값을 적어 주고받는 글자 형식입니다.
// DB에서 찾을 정보가 없으므로 이 예제에는 Service나 Repository가 필요하지 않습니다.

package io.github.greenapple0101.finaccess.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Spring에게 '웹 요청을 받고, 반환한 값을 응답 본문으로 보내는 클래스'라고 알려줍니다.
// 아래 객체를 JSON으로 바꾸는 일은 Spring이 연결한 Jackson 라이브러리가 맡습니다.
@RestController
public class HelloController {

    // 주소가 /api/hello이고 요청 방식이 GET일 때 아래 메서드를 실행합니다.
    @GetMapping("/api/hello")
    public HelloResponse hello() {
        // new는 응답 객체를 만들고, return은 만든 객체를 호출한 쪽으로 돌려줍니다.
        return new HelloResponse("Hello, FinAccess!");
    }

    // record는 전달할 데이터의 모양을 짧게 적는 Java 문법입니다.
    // 여기서는 message라는 이름의 문자열(String) 하나를 담습니다.
    public record HelloResponse(String message) {
    }
}
