package io.github.greenapple0101.finaccess.transfer;

// import는 다른 패키지의 타입을 짧은 이름으로 쓰도록 합니다. 테스트를 실행하는 명령은 아닙니다.
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

// static import 덕분에 Assertions.assertThat 대신 assertThat이라고 쓸 수 있습니다.
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [읽는 순서] 상태 조합 표 → 기대 결과 → 실제 메서드 호출 순으로 보세요.
// Spring을 실행할 필요가 없는 순수 Java 단위 테스트입니다. DB도 사용하지 않습니다.
class TransferStatusTest {
    // @ParameterizedTest는 같은 테스트 메서드를 여러 입력으로 반복 실행합니다.
    // @CsvSource의 한 줄이 한 번의 실행입니다. 쉼표로 현재 상태, 목표 상태, 허용 여부를 나눕니다.
    // JUnit은 문자열을 메서드 매개변수의 enum과 boolean 타입으로 변환해 전달합니다.
    // 4가지 현재 상태 × 4가지 목표 상태 = 16가지 조합을 빠짐없이 검사합니다.
    @ParameterizedTest
    @CsvSource({
            "REQUESTED, REQUESTED, false",
            "REQUESTED, APPROVED, true",
            "REQUESTED, REJECTED, true",
            "REQUESTED, COMPLETED, false",
            "APPROVED, REQUESTED, false",
            "APPROVED, APPROVED, false",
            "APPROVED, REJECTED, false",
            "APPROVED, COMPLETED, true",
            "REJECTED, REQUESTED, false",
            "REJECTED, APPROVED, false",
            "REJECTED, REJECTED, false",
            "REJECTED, COMPLETED, false",
            "COMPLETED, REQUESTED, false",
            "COMPLETED, APPROVED, false",
            "COMPLETED, REJECTED, false",
            "COMPLETED, COMPLETED, false"
    })
    void enforcesTransitionTable(TransferStatus current, TransferStatus next, boolean allowed) {
        if (allowed) {
            // 허용된 경우 반환값이 정확히 목표 상태인지 검사합니다.
            assertThat(current.transitionTo(next)).isEqualTo(next);
        } else {
            // () -> ... 는 나중에 실행할 동작을 전달하는 람다입니다.
            // assertThatThrownBy가 그 동작을 실행하고 발생한 예외를 검사합니다.
            // 예외를 기대하는 이 경우에는 올바른 예외가 발생해야 테스트가 성공합니다.
            assertThatThrownBy(() -> current.transitionTo(next))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot transition from " + current + " to " + next);
        }
    }

    // @EnumSource는 enum의 모든 값을 하나씩 전달하므로 네 번 실행합니다.
    // .class는 타입 정보를 전달하는 문법입니다. 객체를 생성하는 new와 다릅니다.
    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void rejectsMissingTarget(TransferStatus current) {
        assertThatThrownBy(() -> current.transitionTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Target status must be provided");
    }
}
