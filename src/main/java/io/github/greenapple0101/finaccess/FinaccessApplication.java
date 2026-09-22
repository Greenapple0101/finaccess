// [처음 읽기: 이 파일을 사람 말로 번역하면]
// "Java야 main에서 시작해. Spring Boot야 이 클래스를 기준으로 설정을 읽고,
// 앱에 필요한 객체들을 만들고 연결해서 HTTP 요청을 받을 준비를 해줘."
//
// 1. 이 파일이 하는 일
// 회사 등록이나 출금 계산은 하지 않습니다. 프로그램을 시작하는 진입점입니다.
// Gradle의 bootRun 또는 실행 가능한 JAR 실행으로 이 클래스의 main이 호출됩니다.
//
// 2. 폴더가 여러 겹인 이유
// io.github.greenapple0101.finaccess는 클래스의 주소인 패키지 이름입니다.
// 각 단어가 실행 단계라는 뜻은 아닙니다. 하위 company/account/user는 업무별 분류입니다.
// 이 시작 클래스를 루트 패키지에 두어서 하위 코드를 기본 탐색 범위에 포함합니다.
//
// 3. 앱이 시작할 때와 요청이 들어올 때는 다릅니다
// 시작 시: 설정 읽기, Spring 컨테이너 구성, 빈 등록·연결, DB 관련 초기화, 웹 서버 준비.
// 요청 시: HTTP → Tomcat → DispatcherServlet → Controller → Service → Repository → DB.
// main이 매 HTTP 요청마다 다시 실행되는 것은 아닙니다.
//
// 4. Spring 컨테이너(ApplicationContext)
// Spring이 객체의 생성·설정·연결·종료 등을 관리하는 공간입니다.
// 이 공간에 등록된 객체를 빈(Bean)이라고 합니다. 단순한 폴더나 DB 테이블이 아닙니다.
// CompanyController가 CompanyService를 필요로 하면 Spring이 생성자에 전달합니다.
// 객체가 필요한 의존 객체를 직접 만드는 대신 전달받는 것이 의존성 주입(DI)입니다.
// 이런 생성·연결의 제어를 프레임워크에 맡기는 관점을 제어의 역전(IoC)이라고 부릅니다.
//
// 5. 전부 같은 방식으로 찾는 것은 아닙니다
// @RestController, @Service: 컴포넌트 탐색으로 빈 등록.
// JpaRepository 인터페이스: Spring Data JPA의 저장소 인프라가 구현 프록시를 준비.
// @Entity: JPA 매핑 대상으로 탐색. 회사 행마다 Spring 싱글턴 빈을 만드는 것이 아닙니다.
// DataSource 등: 라이브러리·설정·기존 빈 등 조건에 따라 자동 설정으로 구성.
//
// 6. DB 초기화의 의존 관계
// 현재 구성에서는 Flyway가 SQL 이력을 적용한 뒤 Hibernate가 테이블 매핑을 검증합니다.
// DataSource는 DB 연결을 제공하고, Repository는 JPA 기반으로 저장·조회를 수행합니다.
// 전체 빈 생성 순서는 의존 관계와 설정에 따라 결정됩니다.
// "항상 Controller 생성 후 DB 설정" 같은 단일 순서를 외우지 않아도 됩니다.
//
// 7. 실행해 보기
// ./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
// 먼저 Docker와 Compose DB가 켜져 있고 프로젝트 루트에 .env가 있어야 합니다.
// Started FinaccessApplication 로그가 나오면 이 앱의 시작 과정이 완료된 것입니다.
// 다음 읽을 파일: hello/HelloController.java → company/CompanyController.java.

// package는 이 클래스가 속한 이름 공간 선언입니다.
// 예를 들어 company 패키지의 Company와 다른 라이브러리의 Company 이름이 같아도 구분할 수 있습니다.
package io.github.greenapple0101.finaccess;

// import는 다른 패키지 타입을 짧은 이름으로 쓰게 합니다.
// 라이브러리 다운로드는 build.gradle의 역할이며, import 자체가 다운로드하거나 객체를 만들지는 않습니다.
// SpringApplication은 실행 기능, SpringBootApplication은 아래 어노테이션의 타입입니다.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 이 클래스를 Boot의 중심 설정 클래스로 사용합니다.
// @SpringBootConfiguration: 설정 클래스임을 표시합니다.
// @EnableAutoConfiguration: 조건에 맞는 기본 구성을 활성화합니다.
// @ComponentScan: 현재 패키지 아래의 컴포넌트를 찾습니다.
// 예: webmvc 스타터가 있으면 MVC·내장 서버 구성을, JPA 스타터가 있으면 JPA 구성을 준비합니다.
// 이 어노테이션이 혼자 실행되는 것은 아닙니다. run이 시작한 인프라가 메타데이터를 읽습니다.
@SpringBootApplication
// public은 다른 패키지에서도 접근할 수 있다는 뜻이고 class는 타입의 정의입니다.
// 중괄호 { } 안에는 이 클래스에 속한 필드·생성자·메서드 등이 들어갑니다.
public class FinaccessApplication {

	// Java 21에서 이 앱이 사용하는 표준 시작 메서드 형태입니다.
	// public: 외부에서 접근 가능. static: FinaccessApplication 객체를 만들지 않고 클래스에서 호출 가능.
	// void: 이 메서드가 호출자에게 반환하는 값이 없음.
	// String[]: 문자열 배열. args: 실행할 때 받은 인자들의 변수 이름.
	// 예를 들어 --server.port=8081 옵션을 전달하면 Boot가 그 인자를 설정으로 처리할 수 있습니다.
	// 프로그램 시작 전에는 Spring이 준비되지 않았으므로 main은 Spring 빈의 메서드 호출과 다릅니다.
	public static void main(String[] args) {
		// SpringApplication.run은 클래스 이름으로 호출하는 static 메서드입니다.
		// FinaccessApplication.class는 클래스 자체의 정보를 나타내는 Class 객체입니다.
		// new FinaccessApplication()이라는 업무 객체를 전달하는 문법과 다릅니다.
		// 첫 인자는 중심 설정 클래스, 둘째 인자는 실행 인자 배열입니다.
		// run은 애플리케이션 컨텍스트를 반환하지만 여기서는 반환값을 변수에 받지 않습니다.
		// main이 void인 것과 run에 반환값이 있는 것은 모순이 아닙니다.
		// 이 줄을 제거하면 main은 끝나지만 Spring과 웹 서버는 시작되지 않습니다.
		SpringApplication.run(FinaccessApplication.class, args);
	}

}
