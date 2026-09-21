package io.github.greenapple0101.finaccess.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankNames(String name) {
        assertThatThrownBy(() -> new Company(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOneHundredCharactersButRejectsMore() {
        assertThat(new Company("가".repeat(100)).getName()).hasSize(100);
        assertThatThrownBy(() -> new Company("가".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
