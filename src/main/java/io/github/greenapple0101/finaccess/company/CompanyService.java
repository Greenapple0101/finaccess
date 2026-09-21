package io.github.greenapple0101.finaccess.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional
    public RegisteredCompany register(String name) {
        Company company = companyRepository.save(new Company(name));
        return new RegisteredCompany(company.getId(), company.getName());
    }

    @Transactional(readOnly = true)
    public CompanyDetails findById(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        return new CompanyDetails(company.getId(), company.getName(), company.getCreatedAt());
    }

    public record CompanyDetails(UUID id, String name, Instant createdAt) {
    }

    public record RegisteredCompany(UUID id, String name) {
    }
}
