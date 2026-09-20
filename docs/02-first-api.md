# 02. 첫 API와 HTTP 응답 테스트

## 이번 단계의 질문

브라우저나 curl의 요청이 어떻게 Java 메서드를 실행하고 JSON을 돌려받을까요?

## 요청의 흐름

`GET /api/hello` → 내장 Tomcat → Spring MVC의 DispatcherServlet → HelloController.hello() → JSON 응답

DispatcherServlet은 요청을 어떤 처리기로 보낼지 결정하는 Spring MVC의 중심입니다.
`@GetMapping`의 경로와 HTTP 메서드에 맞는 Controller 메서드가 선택됩니다.

## 코드를 한 줄씩 이해하기

```java
@RestController
public class HelloController {
    @GetMapping("/api/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello, FinAccess!");
    }

    public record HelloResponse(String message) {}
}
```

1. `@RestController`: Spring이 이 클래스를 Controller로 등록하고 반환값을 응답 본문으로 쓰도록 합니다.
2. `@GetMapping`: GET 요청 중 `/api/hello` 경로를 이 메서드에 연결합니다.
3. `new HelloResponse(...)`: JSON 문자열을 직접 조립하지 않고 응답 데이터를 담은 Java 객체를 만듭니다.
4. `record`: 데이터를 담는 Java 타입입니다. 여기서는 문자열 `message`를 보관합니다.
5. Spring MVC의 HTTP 메시지 변환기가 객체를 JSON으로 직렬화합니다. 이번 구성에서는 Jackson을 사용합니다.

직렬화는 메모리의 객체를 전송 가능한 표현으로 바꾸는 과정입니다.
`message`라는 컴포넌트가 JSON의 `message` 필드로 나타납니다.
별도 상태 코드를 지정하지 않은 이 정상 응답은 HTTP 200입니다.

## 테스트가 확인하는 것

`HelloControllerTest`는 `@WebMvcTest`로 웹 계층을 구성하고 `MockMvc`로 요청을 흉내 냅니다.
실제 네트워크 포트를 열지는 않지만 경로 매핑과 JSON 직렬화까지 검증합니다.

- 올바른 경로의 GET 요청이 HTTP 200인가?
- 응답 Content-Type이 JSON인가?
- 응답 JSON의 내용이 기대한 메시지인가?

`hello()`를 직접 호출하는 테스트만으로는 잘못된 URL 매핑이나 JSON 변환 문제를 발견하기 어렵습니다.
기존 `contextLoads` 테스트는 전체 애플리케이션의 설정이 로드되는지 확인하는 별도의 역할입니다.

## 직접 실행하기

```bash
./gradlew test
./gradlew bootRun
```

다른 터미널에서:

```bash
curl -i http://localhost:8080/api/hello
```

기대 응답 본문:

```json
{"message":"Hello, FinAccess!"}
```

## 작은 연습

메시지를 다른 값으로 바꾸고 테스트를 실행해 보세요.
테스트가 실패한 이유를 확인한 뒤 기대값을 변경하고 다시 실행해 보세요.
이 연습은 테스트가 외부에 약속한 응답을 검증한다는 사실을 확인하기 위한 것입니다.

## 이번에 추가하지 않은 계층

지금은 고정된 인사말을 반환하므로 Service와 Repository는 필요하지 않습니다.
업무 규칙이 생길 때 Service, 저장할 데이터가 생길 때 Repository를 추가합니다.
