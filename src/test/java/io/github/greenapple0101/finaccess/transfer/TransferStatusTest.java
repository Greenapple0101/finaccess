// [이 파일의 역할] 이체 상태를 정해진 순서로만 바꿀 수 있는지 확인합니다.
// 요청 → 승인 → 완료 또는 요청 → 반려가 가능한 길입니다. 다른 길은 거절해야 합니다.
// 아래 표 읽기 → current(현재), next(다음), allowed(허용 여부) 받기 → 결과 확인 순서입니다.
// Spring과 DB 없이 규칙만 시험합니다. 상태를 바꿔 볼 뿐 실제 돈을 옮기지 않습니다.

package io.github.greenapple0101.finaccess.transfer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferStatusTest {
    // @CsvSource의 한 줄이 시험 한 번입니다. 쉼표로 현재 상태, 목표 상태, 허용 여부를 나눕니다.
    // 예: REQUESTED, APPROVED, true는 "요청됨에서 승인됨으로 갈 수 있어야 한다"는 뜻입니다.
    // JUnit이 글을 상태 값과 true/false로 바꿔 전달합니다. 4 × 4 = 16가지 조합을 확인합니다.
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
            // 허용된 길이라면 반환값이 원하는 다음 상태여야 합니다.
            assertThat(current.transitionTo(next)).isEqualTo(next);
        } else {
            // () -> ...는 실행할 동작을 전달하는 문법입니다. assertThatThrownBy가 실행하고 오류를 확인합니다.
            // 거절해야 하는 길이므로 IllegalStateException이 발생하면 시험이 통과합니다.
            assertThatThrownBy(() -> current.transitionTo(next))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cannot transition from " + current + " to " + next);
        }
    }

    // @EnumSource가 현재 상태 네 가지를 하나씩 전달합니다. 어느 상태든 목표가 없으면 거절해야 합니다.
    // TransferStatus.class는 상태 타입을 알려주는 표현입니다. 새 요청서를 만드는 문법이 아닙니다.
    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void rejectsMissingTarget(TransferStatus current) {
        assertThatThrownBy(() -> current.transitionTo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Target status must be provided");
    }
}
