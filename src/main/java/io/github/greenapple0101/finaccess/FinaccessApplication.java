package io.github.greenapple0101.finaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// [읽기 1] 이 파일은 프로그램의 시작점입니다.
// @SpringBootApplication은 자동 설정과 컴포넌트 탐색 등을 활성화합니다.
// Spring은 이 패키지와 하위 패키지에서 Controller, Service, Entity 등을 찾아 구성합니다.
// Bean(빈)은 Spring이 생성하고 연결해 관리하는 객체를 뜻합니다.
@SpringBootApplication
public class FinaccessApplication {

	// Java가 실행을 시작하는 main 메서드입니다.
	// public은 외부에서 접근 가능, static은 객체를 만들지 않고 호출 가능, void는 반환값이 없다는 뜻입니다.
	// String[] args는 실행할 때 전달한 옵션들의 배열입니다.
	// SpringApplication.run이 Spring 컨테이너와 내장 웹 서버를 시작합니다.
	public static void main(String[] args) {
		SpringApplication.run(FinaccessApplication.class, args);
	}

}
