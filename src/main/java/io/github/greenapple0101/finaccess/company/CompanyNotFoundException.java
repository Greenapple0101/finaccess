package io.github.greenapple0101.finaccess.company;

import java.util.UUID;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(UUID id) {
        super("Company not found: " + id);
    }
}
