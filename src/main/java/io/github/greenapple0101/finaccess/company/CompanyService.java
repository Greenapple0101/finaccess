// [이 파일의 역할: 회사 등록·조회라는 업무 단위]
// Controller가 HTTP에서 꺼낸 이름이나 ID를 받아 Repository를 이용합니다.
// 이 파일은 HTTP 201/404를 결정하지 않습니다. 결과나 예외로 상황을 전달합니다.
// Controller는 이 결과를 응답에 담고 예외를 HTTP 오류로 바꿉니다.
//
// 두 가지 작업이 있습니다.
// register: 이름 검증을 포함한 Company 생성 → 저장 → 등록 결과 DTO.
// findById: 조회 → 없으면 예외 → 있으면 조회 결과 DTO.
// DTO는 Data Transfer Object, 데이터를 전달하기 위한 타입이라는 뜻입니다.
//
// Service가 필요한 이유
// 지금은 짧지만 여러 저장소 호출·업무 규칙이 늘어날 때 하나의 작업 경계를 둘 수 있습니다.
// Repository의 save 한 번보다 큰 트랜잭션 단위를 표현할 위치이기도 합니다.
// 컴포넌트 탐색 대상이라는 표시입니다. Spring이 객체를 만들고 의존 객체를 연결합니다.
// Controller에는 보통 원본 객체 앞에서 부가 작업을 처리하는 프록시가 주입될 수 있습니다.
// 여기서는 그 프록시가 @Transactional을 읽어 트랜잭션 처리를 감쌉니다.
// @Service만으로 트랜잭션이 생기는 것은 아니며 아래 @Transactional이 경계를 선언합니다.

// package는 이 파일의 소속 주소입니다. 계층별 호출 순서를 나타내는 문법은 아닙니다.
// 아래 import는 다른 패키지의 타입을 짧은 이름으로 쓰기 위한 선언이며, 실행이나 객체 생성이 아닙니다.
package io.github.greenapple0101.finaccess.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    // 생성자 주입입니다. 매개변수 타입에 맞는 Repository 빈을 Spring이 찾아 전달합니다.
    // 이 저장소는 우리가 구현 클래스를 쓰지 않아도 Spring Data JPA가 제공한 프록시 객체입니다.
    // Controller → Service → Repository는 폴더 중첩이 아니라 객체 참조 관계입니다.
    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    // 트랜잭션은 함께 성공시키거나 실패 시 함께 되돌릴 DB 작업의 경계입니다.
    // 일반적인 외부 호출 흐름: Controller → Service 프록시 → 트랜잭션 시작 → 메서드 실행
    // → flush/commit → Controller가 결과를 받음. 커밋 자체가 실패하면 성공 응답으로 이어지지 않습니다.
    // 기본 전파 속성 REQUIRED이므로 이미 트랜잭션이 있으면 참여하고, 없으면 새로 시작합니다.
    // 기본적으로 RuntimeException/Error는 롤백 대상이며 모든 checked exception이 자동 롤백되는 것은 아닙니다.
    // 같은 객체 내부의 this.메서드() 호출은 기본 프록시 방식의 경계를 거치지 않습니다.
    // 직접 new로 만든 Service에 어노테이션만 붙어 있어도 자동 트랜잭션이 생기는 것은 아닙니다.
    @Transactional
    public RegisteredCompany register(String name) {
        // 읽는 순서: new Company(name) → 생성자의 이름 검사 → Repository.save → 결과 참조 대입.
        // 지역변수 company는 이 메서드 실행 중 사용하는 이름입니다. Controller의 필드와 다릅니다.
        // save는 새 엔티티를 영속화하거나 기존 엔티티 상태를 병합합니다. 무조건 즉시 INSERT한다는 뜻은 아닙니다.
        // 현재 UUID가 null인 새 객체는 신규로 판단됩니다. 실제 SQL은 flush까지 지연될 수 있습니다.
        Company company = companyRepository.save(new Company(name));
        return new RegisteredCompany(company.getId(), company.getName());
    }

    // 조회 목적의 트랜잭션이라는 힌트입니다. 쓰기 권한을 차단하는 보안 기능이 아닙니다.
    // 조회한 엔티티에서 필요한 값을 트랜잭션 안에서 읽어 응답 DTO로 만듭니다.
    // open-in-view=false이므로 웹 응답 생성까지 지연 로딩에 의존하지 않는 설계에 도움이 됩니다.
    @Transactional(readOnly = true)
    public CompanyDetails findById(UUID id) {
        // findById 반환 타입 Optional<Company>는 "회사 한 개가 있거나 없다"를 표현합니다.
        // orElseThrow는 값이 있으면 Company를 꺼내고, 없으면 전달한 함수로 예외를 만듭니다.
        // () -> new CompanyNotFoundException(id)는 인자 없는 람다식이며 필요할 때 예외 객체를 생성합니다.
        // 없을 때 null을 그대로 반환하여 나중에 원인 모를 NullPointerException이 생기는 것을 피합니다.
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        return new CompanyDetails(company.getId(), company.getName(), company.getCreatedAt());
    }

    // 조회 결과용 불변 데이터 묶음입니다. UUID·String·Instant 값을 전달합니다.
    // 엔티티 자체를 반환하지 않아 JPA의 지연 로딩 관계가 응답 직렬화에 섞이지 않게 합니다.
    // Instant는 특정 시점을 나타냅니다. 표시할 한국 시간 문자열과 같은 개념은 아닙니다.
    public record CompanyDetails(UUID id, String name, Instant createdAt) {
    }

    // 등록 결과는 id와 name만 전달합니다.
    // created_at은 DB 기본값이 채우며 현재 엔티티 매핑은 저장 직후 자동 재조회하지 않습니다.
    // 따라서 등록 결과에 생성 시각을 넣으려면 별도 반영 전략이 필요합니다.
    public record RegisteredCompany(UUID id, String name) {
    }
}
