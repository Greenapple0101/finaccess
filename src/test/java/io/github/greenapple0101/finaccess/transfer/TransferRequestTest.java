// [이 파일의 역할] 이체 요청서를 만들 때 필요한 값과 최초 상태를 확인합니다.
// Spring이나 DB를 켜지 않고 Java 객체만 만드는 '단위 테스트'입니다.
// 각 시험은 ID 준비 → new TransferRequest(...) 실행 → 보관한 값 또는 오류 확인 순서입니다.
// assertThat(실제값).isEqualTo(기대값)은 값이 맞는지 확인하는 문장입니다.
// assertThatThrownBy(() -> 동작)은 그 동작이 오류(예외)로 거절되어야 통과합니다.
// 이 시험은 ID가 비어 있는지 등을 검사합니다. 그 계좌나 사용자가 DB에 실제로 있는지는 확인하지 않습니다.

package io.github.greenapple0101.finaccess.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRequestTest {
    // randomUUID는 식별 번호만 만듭니다. 계좌나 사용자를 실제 DB에 등록하는 코드는 아닙니다.
    private final UUID sourceId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    // 1,000원 요청서를 만들면 전달한 네 값이 보관되고, 상태는 REQUESTED(요청됨)로 시작해야 합니다.
    @Test
    void preservesRequestDetailsAndStartsRequested() {
        TransferRequest request = new TransferRequest(sourceId, destinationId, 1000L, requesterId);
        assertThat(request.getSourceAccountId()).isEqualTo(sourceId);
        assertThat(request.getDestinationAccountId()).isEqualTo(destinationId);
        assertThat(request.getAmountWon()).isEqualTo(1000L);
        assertThat(request.getRequesterId()).isEqualTo(requesterId);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    // 출금 계좌 ID가 null(없음)이면 생성을 거절해야 합니다. hasMessage는 오류 안내 문구도 확인합니다.
    @Test
    void rejectsMissingSourceAccount() {
        assertThatThrownBy(() -> new TransferRequest(null, destinationId, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source account ID must be provided");
    }

    // 돈을 받을 계좌 ID가 빠져도 요청서를 만들 수 없어야 합니다.
    @Test
    void rejectsMissingDestinationAccount() {
        assertThatThrownBy(() -> new TransferRequest(sourceId, null, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destination account ID must be provided");
    }

    // 요청자 ID가 빠진 요청서는 거절해야 합니다.
    @Test
    void rejectsMissingRequester() {
        assertThatThrownBy(() -> new TransferRequest(sourceId, destinationId, 1000L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requester ID must be provided");
    }

    // 같은 ID를 글로 바꿨다가 다시 UUID로 만듭니다.
    // 객체가 따로 만들어졌더라도 ID 값이 같으면 같은 계좌이므로 거절해야 합니다.
    @Test
    void rejectsSameAccountByIdValue() {
        UUID sameAccountId = UUID.fromString(sourceId.toString());
        assertThatThrownBy(() -> new TransferRequest(sourceId, sameAccountId, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source and destination accounts must differ");
    }

    // @ValueSource가 0원·-1원·long 최솟값을 하나씩 넣어 이 시험을 세 번 실행합니다.
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveAmount(long amountWon) {
        assertThatThrownBy(() -> new TransferRequest(sourceId, destinationId, amountWon, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    // 양수 중 가장 작은 1과 long에 담을 수 있는 가장 큰 정수를 넣어 봅니다.
    // 금액 보관 범위를 검사하는 것이며, 실제 서비스의 이체 한도를 정하는 시험은 아닙니다.
    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void acceptsPositiveAmountBoundaries(long amountWon) {
        TransferRequest request = new TransferRequest(sourceId, destinationId, amountWon, requesterId);
        assertThat(request.getAmountWon()).isEqualTo(amountWon);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }
}
