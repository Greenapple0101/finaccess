// [이 파일이 하는 일] FinAccess 프로그램을 켜는 시작점입니다.
// 앱을 실행하면 main → SpringApplication.run 순서로 시작합니다.
// Spring은 설정을 읽고 필요한 객체와 DB 연결을 준비한 뒤 웹 요청을 받을 수 있게 합니다.
// 회사 등록이나 이체 계산은 다른 파일에서 처리합니다. main이 요청마다 다시 실행되지는 않습니다.
//
// [먼저 알면 좋은 말]
// 객체: 회사 한 곳, 계좌 하나처럼 프로그램 안에서 정보와 동작을 묶은 것입니다.
// Bean(빈): Spring이 만들고 관리하는 객체입니다. Spring이 관리하는 공간을 '컨테이너'라고 부릅니다.
// 예를 들어 Controller가 Service를 필요로 하면 Spring이 준비한 Service를 건네줍니다.
// 필요한 객체를 직접 만들지 않고 받는 방식을 '의존성 주입(DI)'이라고 합니다.
//
// 폴더가 여러 겹이어도 서버가 여러 개라는 뜻은 아닙니다.
// company는 회사 코드, account는 계좌 코드처럼 관련 파일을 모아 놓은 폴더입니다.
// 처음에는 이 파일 → hello/HelloController.java → company/CompanyController.java 순서로 읽으세요.

// package는 이 클래스의 주소입니다. 이름이 같은 다른 클래스와 구분하는 데 씁니다.
package io.github.greenapple0101.finaccess;

// import는 긴 주소를 매번 쓰지 않고 SpringApplication처럼 짧게 부르게 합니다.
// 실제 라이브러리를 내려받는 일은 build.gradle을 읽는 Gradle이 합니다.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot에게 '이 클래스를 기준으로 앱 설정을 준비해 줘'라고 표시합니다.
// 현재 패키지 아래의 Controller·Service를 찾고, 설치한 라이브러리에 맞는 기본 설정을 준비합니다.
// 단, Entity는 DB 데이터와 연결할 대상이고 Repository 구현은 Spring Data JPA가 준비합니다.
// 모든 Java 파일이 똑같은 방식으로 하나씩 Bean이 되는 것은 아닙니다.
@SpringBootApplication
public class FinaccessApplication {

	// main은 이 앱에서 Java가 실행을 시작하는 메서드(동작)입니다.
	// public: 밖에서 호출 가능 / static: 이 클래스의 객체를 먼저 만들지 않아도 호출 가능.
	// void: 돌려주는 값 없음 / String[] args: 실행할 때 받은 글자 목록.
	// 예: --server.port=8081을 전달하면 사용할 서버 포트 설정에 반영됩니다.
	public static void main(String[] args) {
		// 첫 값은 앱 설정의 기준 클래스 정보(.class), 둘째 값은 실행 옵션입니다.
		// run은 Spring이 관리하는 공간을 돌려주지만 여기서는 변수로 받지 않습니다.
		// 이 한 줄이 있어야 Spring과 웹 서버가 시작됩니다.
		SpringApplication.run(FinaccessApplication.class, args);
	}

}
