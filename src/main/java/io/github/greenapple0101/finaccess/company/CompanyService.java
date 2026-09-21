package io.github.greenapple0101.finaccess.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;

// [읽기 4] 회사 등록·조회 작업을 담당하는 계층입니다.
// @Service는 이 클래스를 Spring이 관리할 객체로 등록합니다.
// 여기서는 HTTP 상태 코드를 다루지 않습니다. 그 판단은 Controller가 맡습니다.
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    // Repository 구현 객체를 Spring으로부터 전달받습니다.
    // Service는 SQL 작성 방법보다 어떤 작업을 수행할지에 집중합니다.
    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    // Spring의 프록시가 이 메서드 호출을 감싸 트랜잭션을 시작하고 정상 완료 시 커밋합니다.
    // 기본적으로 RuntimeException이나 Error가 전달되면 롤백합니다.
    // 같은 객체 내부에서 직접 호출하면 일반적인 프록시 방식에서는 이 기능이 적용되지 않습니다.
    // new Company(name)은 이름을 검사하며 객체만 만듭니다.
    // save는 JPA에 저장을 요청합니다. SQL 실행과 커밋은 메서드 호출 순간과 다를 수 있습니다.
    @Transactional
    public RegisteredCompany register(String name) {
        Company company = companyRepository.save(new Company(name));
        return new RegisteredCompany(company.getId(), company.getName());
    }

    // 조회 목적의 트랜잭션이라는 힌트입니다. 접근 권한을 검사하거나 모든 쓰기를 금지하는 장치는 아닙니다.
    // findById의 Optional은 회사가 없을 가능성을 표현합니다.
    // orElseThrow는 값이 있으면 꺼내고, 없으면 예외를 던집니다.
    // () -> new ... 는 예외가 필요할 때 생성하는 람다식입니다.
    @Transactional(readOnly = true)
    public CompanyDetails findById(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        return new CompanyDetails(company.getId(), company.getName(), company.getCreatedAt());
    }

    // 조회 결과 DTO입니다. DB에서 읽은 생성 시각까지 전달합니다.
    // 엔티티 자체를 반환하지 않아 DB 매핑과 HTTP 응답 형식을 분리합니다.
    public record CompanyDetails(UUID id, String name, Instant createdAt) {
    }

    // 등록 결과 DTO입니다. 등록 직후 DB 생성 시각을 다시 읽지 않으므로 id와 name만 포함합니다.
    public record RegisteredCompany(UUID id, String name) {
    }
}
