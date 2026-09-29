// [회사 등록·조회를 처리하는 담당자]
// Controller에서 받은 이름이나 ID로 저장소를 사용하고, 결과를 정리해서 돌려줍니다.
// 등록 순서: 이름을 검사하며 Company 만들기 → 저장 요청 → ID와 이름을 결과로 반환.
// 조회 순서: ID로 회사 찾기 → 없으면 중단 → 있으면 회사 정보를 결과로 반환.
//
// Service는 "일을 어떤 순서로 할지", Repository는 "DB에 어떻게 접근할지" 맡습니다.
// HTTP 201·404 같은 응답 선택은 CompanyController가 맡습니다.
// 아래 record들은 결과값을 담는 묶음입니다. 이런 전달용 데이터 묶음을 DTO라고도 부릅니다.

package io.github.greenapple0101.finaccess.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;

// Spring에게 "이 클래스를 업무 담당 객체로 만들어 관리해 주세요"라고 표시합니다.
// Spring이 만들어 관리하는 객체를 빈(Bean)이라고 부릅니다.
// @Service 자체가 DB 변경을 한 묶음으로 처리해 주는 것은 아닙니다. 그 설정은 @Transactional입니다.
@Service
public class CompanyService {

    // 회사 저장소를 보관하는 자리입니다. 여러 메서드에서 같은 저장소 기능을 사용합니다.
    private final CompanyRepository companyRepository;

    // Service를 만들 때 Spring이 준비한 저장소를 전달받습니다.
    // 이 연결을 생성자 주입이라고 합니다. 우리가 직접 new CompanyRepository()를 만들지는 않습니다.
    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    // 트랜잭션은 DB 작업을 함께 성공시키거나, 실패하면 함께 되돌리는 작업 묶음입니다.
    // 일반적인 Controller 호출에서는 Spring의 중간 담당(프록시)이 트랜잭션을 시작하고 이 메서드를 실행합니다.
    // 처리가 끝나면 변경을 확정(commit)한 뒤 Controller에 결과를 전달합니다. 확정이 실패해도 오류가 납니다.
    // 이미 트랜잭션이 있으면 그 작업 묶음에 참여합니다. 기본적으로 RuntimeException이 발생하면 되돌립니다.
    // 직접 new로 만든 Service 호출이나 같은 객체 안에서의 호출에는 이 중간 담당이 끼어들지 않습니다.
    @Transactional
    public RegisteredCompany register(String name) {
        // 1. 회사 객체를 만듭니다. 이름이 비었거나 너무 길면 생성자에서 중단됩니다.
        Company newCompany = new Company(name);

        // 2. 저장을 요청하고 저장소가 돌려준 객체를 받습니다. 새 객체에는 아직 ID가 없어 신규 등록합니다.
        // save를 호출해도 실제 INSERT SQL은 트랜잭션 마무리까지 기다렸다가 실행될 수 있습니다.
        Company savedCompany = companyRepository.save(newCompany);
        // 3. 저장한 회사에서 ID와 이름만 꺼내 결과 묶음을 만듭니다.
        // 회사 객체 자체를 내보내지 않고, 응답에 필요한 항목을 골라 전달하는 것입니다.
        return new RegisteredCompany(savedCompany.getId(), savedCompany.getName());
    }

    // 조회용 작업임을 Spring/JPA에 알려 줍니다. readOnly는 읽기 전용 처리에 쓸 수 있는 힌트입니다.
    // 쓰기 권한을 막는 보안 장치는 아니므로 이것만으로 수정이 무조건 차단되지는 않습니다.
    @Transactional(readOnly = true)
    public CompanyDetails findById(UUID id) {
        // findById는 "회사 있음/없음"을 Optional에 담아 돌려줍니다.
        // orElseThrow는 회사가 있으면 꺼내고, 없으면 예외를 보내 이 작업을 중단합니다.
        // () -> new ... 는 "필요할 때 이 예외를 만들어라"라는 짧은 함수 표현(람다)입니다.
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        // 트랜잭션 안에서 필요한 값을 꺼내 평범한 결과 데이터로 만듭니다.
        // HTTP 응답을 만드는 단계에서 뒤늦게 DB 정보를 더 읽지 않아도 됩니다.
        return new CompanyDetails(company.getId(), company.getName(), company.getCreatedAt());
    }

    // 조회 결과의 모양입니다. 한 회사의 ID·이름·등록 시각을 함께 전달합니다.
    // record는 생성자와 id(), name(), createdAt() 같은 읽기 메서드를 자동으로 제공하는 Java 문법입니다.
    public record CompanyDetails(UUID id, String name, Instant createdAt) {
    }

    // 등록 결과에는 ID와 이름만 담습니다.
    // 등록 시각은 DB가 채우지만 저장 직후 Java 객체로 자동 복사되지는 않아 여기서는 제외했습니다.
    public record RegisteredCompany(UUID id, String name) {
    }
}
