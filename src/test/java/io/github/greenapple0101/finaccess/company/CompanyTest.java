// [테스트 파일을 처음 읽는 방법]
// 이 파일은 운영 요청을 처리하는 코드가 아니라, 기존 코드가 약속을 지키는지 자동 확인하는 코드입니다.
// ./gradlew test를 실행하면 JUnit이 @Test 또는 @ParameterizedTest 메서드를 찾아 실행합니다.
// 각 테스트는 준비(객체·데이터) → 실행(메서드·요청) → 검증(기대한 값)의 순서로 읽으세요.
// 테스트 메서드가 파일에 적힌 순서대로 실행된다고 가정하면 안 됩니다.
//
// assertThat(실제값).isEqualTo(기대값): 값이 같아야 성공합니다.
// isZero/isNotNull/isEmpty 등은 같은 방식으로 기대 조건을 표현합니다.
// assertThatThrownBy(() -> 호출): 람다를 실행할 때 예외가 발생하는지 확인합니다.
// 예외를 기대한 테스트에서 그 예외가 나면 테스트는 성공할 수 있습니다.
// 테스트 실패는 기대 결과와 실제 결과가 다르다는 뜻이며, 전체 프로그램이 잘못됐다는 뜻만은 아닙니다.
//
// @ParameterizedTest는 같은 검증을 여러 입력에 반복합니다.
// @ValueSource는 반복할 문자열·숫자들을 제공하고, @NullAndEmptySource는 null·빈 값을 제공합니다.
// 예를 들어 0, -1, Long.MIN_VALUE를 지정하면 각각 별도의 테스트 실행으로 집계됩니다.
// Long.MAX_VALUE/MIN_VALUE는 long 표현 범위의 끝값이며 실제 서비스 거래 한도는 아닙니다.

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
