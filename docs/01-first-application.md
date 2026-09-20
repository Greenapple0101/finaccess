# 01. Spring Boot 애플리케이션 시작하기

## 이번 단계의 질문

Java 프로그램을 실행했는데 어떻게 HTTP 요청을 받는 서버가 될까요?

## 시작 흐름

1. `./gradlew bootRun`이 프로젝트에 지정된 Gradle로 실행 작업을 시작합니다.
2. `FinaccessApplication.main()`이 실행됩니다.
3. `SpringApplication.run()`이 Spring 컨테이너를 생성하고 설정을 읽습니다.
4. Web MVC 스타터에 포함된 내장 Tomcat이 시작되어 HTTP 요청을 기다립니다.

Spring 컨테이너는 애플리케이션 객체(Bean)를 생성하고 연결하는 역할을 합니다.
`@SpringBootApplication`은 자동 설정과 현재 패키지 아래의 컴포넌트 탐색 등을 활성화합니다.
앞으로 Controller도 이 패키지 아래에 두면 Spring이 찾아서 등록합니다.

## 파일별 역할

| 파일 | 역할 |
| --- | --- |
| `build.gradle` | Java 버전, 라이브러리 의존성, 테스트 설정 |
| `settings.gradle` | 프로젝트 이름 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | 팀원이 동일한 Gradle 버전을 사용하도록 지원 |
| `FinaccessApplication.java` | 애플리케이션 시작점 |
| `application.properties` | 애플리케이션 설정 |
| `FinaccessApplicationTests.java` | Spring 컨텍스트가 정상적으로 시작되는지 확인 |

## 버전 선택

설치된 Java 21과 공식 Spring Initializr가 제공하는 안정 버전 Spring Boot 4.1.1을 사용합니다.
참고 블로그의 Spring Boot 3.x와 패키지·의존성이 다를 수 있습니다.
이번에는 Boot 4의 `spring-boot-starter-webmvc`와 `spring-boot-starter-webmvc-test`를 사용합니다.

- 공식 생성기: https://start.spring.io/
- 시스템 요구사항: https://docs.spring.io/spring-boot/system-requirements.html

## 직접 확인하기

```bash
./gradlew test
./gradlew bootRun
```

로그에서 `Started FinaccessApplication`을 확인합니다.
아직 경로를 등록하지 않은 첫 커밋에서는 `/` 요청이 404인 것이 정상입니다.
서버 실행 성공과 특정 API 구현 완료는 다른 의미입니다.

## 생각해 볼 질문

- 직접 Tomcat을 설치하지 않았는데 서버가 실행되는 이유는 무엇인가요?
- `contextLoads` 테스트가 통과해도 특정 API가 올바르게 응답한다고 보장할 수 있을까요?
