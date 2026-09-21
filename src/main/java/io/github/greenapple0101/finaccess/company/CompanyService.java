package io.github.greenapple0101.finaccess.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    public record RegisteredCompany(UUID id, String name) {
    }
}
