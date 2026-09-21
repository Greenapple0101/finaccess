package io.github.greenapple0101.finaccess.user;

import io.github.greenapple0101.finaccess.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "identity_issuer", nullable = false, length = 512, updatable = false)
    private String identityIssuer;

    @Column(name = "identity_subject", nullable = false, length = 255, updatable = false)
    private String identitySubject;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(Company company, String identityIssuer, String identitySubject) {
        if (company == null) {
            throw new IllegalArgumentException("Company must be provided");
        }
        validateIdentityValue(identityIssuer, 512, "Identity issuer");
        validateIdentityValue(identitySubject, 255, "Identity subject");
        this.company = company;
        this.identityIssuer = identityIssuer;
        this.identitySubject = identitySubject;
    }

    private static void validateIdentityValue(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + " must be non-blank and at most " + maxLength + " characters");
        }
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public String getIdentityIssuer() { return identityIssuer; }
    public String getIdentitySubject() { return identitySubject; }
    public Instant getCreatedAt() { return createdAt; }
}
