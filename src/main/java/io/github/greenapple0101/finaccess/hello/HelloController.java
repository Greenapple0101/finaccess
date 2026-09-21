package io.github.greenapple0101.finaccess.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// [읽기 2] DB 없이 HTTP 요청과 응답만 이해하는 가장 작은 예제입니다.
// @RestController는 이 클래스를 웹 요청 처리 객체로 등록하고 반환값을 응답 본문에 쓰도록 합니다.
@RestController
public class HelloController {

    // GET /api/hello 요청을 아래 hello 메서드에 연결합니다.
    // 메서드가 반환한 HelloResponse 객체는 메시지 변환기를 거쳐 JSON이 됩니다.
    // 정상 응답 예: {"message":"Hello, FinAccess!"}, HTTP 상태는 200입니다.
    @GetMapping("/api/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello, FinAccess!");
    }

    // record는 여러 값을 담아 전달하기 위한 Java 타입입니다.
    // 여기서는 message 한 값을 담으며 생성자와 message() 접근자 등이 자동 제공됩니다.
    public record HelloResponse(String message) {
    }
}
