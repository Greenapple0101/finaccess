package io.github.greenapple0101.finaccess.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Spring이나 DB를 실행하지 않고 생성자의 업무 규칙을 검증합니다.
// 각 메서드는 준비 → 생성자 실행 → 결과 확인 순으로 읽으세요.
class TransferRequestTest {
    // randomUUID는 테스트용 ID를 만듭니다. 실제 계좌나 사용자를 DB에 등록하지는 않습니다.
    private final UUID sourceId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    // JUnit은 @Test가 붙은 메서드를 테스트로 실행합니다.
    @Test
    void preservesRequestDetailsAndStartsRequested() {
        TransferRequest request = new TransferRequest(sourceId, destinationId, 1000L, requesterId);
        // assertThat은 실제 값을 받고, isEqualTo는 기대한 값과 같은지 검사합니다.
        assertThat(request.getSourceAccountId()).isEqualTo(sourceId);
        assertThat(request.getDestinationAccountId()).isEqualTo(destinationId);
        assertThat(request.getAmountWon()).isEqualTo(1000L);
        assertThat(request.getRequesterId()).isEqualTo(requesterId);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    @Test
    void rejectsMissingSourceAccount() {
        // () -> 는 실행할 동작을 전달하는 람다입니다. 여기서는 생성자 호출을 전달합니다.
        // 예외가 발생하는 것이 정상인 시나리오이므로, 지정한 예외가 발생해야 테스트가 통과합니다.
        assertThatThrownBy(() -> new TransferRequest(null, destinationId, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source account ID must be provided");
    }

    @Test
    void rejectsMissingDestinationAccount() {
        assertThatThrownBy(() -> new TransferRequest(sourceId, null, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Destination account ID must be provided");
    }

    @Test
    void rejectsMissingRequester() {
        assertThatThrownBy(() -> new TransferRequest(sourceId, destinationId, 1000L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requester ID must be provided");
    }

    @Test
    void rejectsSameAccountByIdValue() {
        // 같은 계좌 ID를 문자열로 바꿨다가 다시 UUID로 만듭니다.
        // 동일한 객체 참조인지가 아니라 ID 값이 같은지를 검사해야 한다는 사례입니다.
        UUID sameAccountId = UUID.fromString(sourceId.toString());
        assertThatThrownBy(() -> new TransferRequest(sourceId, sameAccountId, 1000L, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source and destination accounts must differ");
    }

    // 같은 테스트를 0, -1, long의 최솟값으로 세 번 실행합니다.
    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveAmount(long amountWon) {
        assertThatThrownBy(() -> new TransferRequest(sourceId, destinationId, amountWon, requesterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    // 양수의 양 끝을 검사합니다. 자료형 경계 테스트이며 실제 금융 거래 한도를 뜻하지 않습니다.
    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void acceptsPositiveAmountBoundaries(long amountWon) {
        TransferRequest request = new TransferRequest(sourceId, destinationId, amountWon, requesterId);
        assertThat(request.getAmountWon()).isEqualTo(amountWon);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }
}
