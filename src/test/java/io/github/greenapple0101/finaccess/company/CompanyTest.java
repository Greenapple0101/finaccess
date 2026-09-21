package io.github.greenapple0101.finaccess.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// DB나 Spring을 실행하지 않는 단위 테스트입니다.
// Company 생성자가 잘못된 이름을 거부하는 규칙만 빠르게 확인합니다.
class CompanyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    // NullAndEmptySource와 ValueSource가 제공하는 값마다 테스트를 실행합니다.
    // assertThatThrownBy는 코드를 실행했을 때 지정한 예외가 발생해야 성공하는 검증입니다.
    void rejectsBlankNames(String name) {
        assertThatThrownBy(() -> new Company(name)).isInstanceOf(IllegalArgumentException.class);
    }

    // JUnit이 실행할 테스트 메서드라는 표시입니다.
    @Test
    // 최대 허용값 100자와 그 바로 다음 값 101자를 검사하는 경계값 테스트입니다.
    void acceptsOneHundredCharactersButRejectsMore() {
        assertThat(new Company("가".repeat(100)).getName()).hasSize(100);
        assertThatThrownBy(() -> new Company("가".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
