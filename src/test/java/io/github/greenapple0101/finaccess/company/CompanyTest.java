// [이 파일의 역할] 회사 이름을 잘못 적으면 Company가 생성을 거절하는지 확인합니다.
// Spring과 DB를 켜지 않는 작은 시험입니다. 이를 '단위 테스트'라고 합니다.
// 준비한 이름으로 new Company(...)를 실행한 뒤, 결과나 오류를 확인하는 순서로 읽으세요.
// @Test는 시험 한 개, @ParameterizedTest는 입력값을 바꿔 반복하는 시험입니다.
// assertThatThrownBy(() -> 동작)은 해당 동작에서 오류(예외)가 발생하는지 확인합니다.
// 여기서는 잘못된 이름을 거절해야 정답이므로, 예상한 오류가 나야 시험이 통과합니다.

package io.github.greenapple0101.finaccess.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTest {

    // 아래 표시가 null, 빈 글, 공백, 탭, 줄바꿈을 하나씩 name에 넣어 같은 시험을 반복합니다.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankNames(String name) {
        assertThatThrownBy(() -> new Company(name)).isInstanceOf(IllegalArgumentException.class);
    }

    // "가"를 100번 반복하면 허용, 101번 반복하면 거절되어야 합니다.
    // 허용 범위의 끝과 그 바로 밖을 확인하는 방법을 경계값 테스트라고 합니다.
    @Test
    void acceptsOneHundredCharactersButRejectsMore() {
        assertThat(new Company("가".repeat(100)).getName()).hasSize(100);
        assertThatThrownBy(() -> new Company("가".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
