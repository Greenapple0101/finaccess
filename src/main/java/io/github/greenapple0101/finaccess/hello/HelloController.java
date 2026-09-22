// [이 파일의 역할: 가장 작은 HTTP 요청·응답]
// 서버가 켜진 뒤 GET /api/hello를 받으면 인사말을 반환합니다.
// DB나 업무 규칙이 없어 Service와 Repository를 만들지 않았습니다.
// "Controller가 있으면 항상 Service를 만들어야 한다"는 Java 규칙은 없습니다.
//
// 요청: 브라우저/curl → 내장 Tomcat → Spring MVC DispatcherServlet.
// DispatcherServlet은 등록된 경로 정보를 보고 hello 메서드로 요청을 연결합니다.
// 반환: HelloResponse 객체 → HTTP 메시지 변환기(Jackson) → JSON 본문.
// 객체를 전송용 표현으로 바꾸는 과정을 직렬화라고 합니다.
// 이 클래스가 직접 소켓을 열거나 JSON 문자열을 조립하는 것은 아닙니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @Controller + @ResponseBody의 의미를 함께 가집니다.
// Spring의 웹 컴포넌트로 등록하고, 반환값을 화면 이름이 아니라 응답 본문으로 처리합니다.
// 객체 반환은 현재 구성에서 Jackson이 JSON으로 변환합니다.
// 어노테이션 자체가 실행문은 아니며 Spring MVC가 이 정보를 읽어 동작을 연결합니다.
@RestController
public class HelloController {

    // GET /api/hello 요청 시 아래 메서드를 호출하도록 등록합니다.
    // 매 요청마다 새 응답 객체를 만들지만 Controller 빈을 새로 만드는 코드는 아닙니다.
    @GetMapping("/api/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello, FinAccess!");
    }

    // record는 message라는 문자열 값을 담는 응답 타입입니다.
    // new HelloResponse("Hello, FinAccess!")가 객체를 만들고 Jackson이 {"message":"..."}로 바꿉니다.
    public record HelloResponse(String message) {
    }
}
