package io.github.greenapple0101.finaccess.transfer;

// 요청자의 소속 회사와 출금 계좌의 소유 회사가 다를 때 발생합니다.
// 이 검사만으로 로그인한 본인의 요청이라고 보장할 수는 없습니다.
public class TransferCompanyMismatchException extends RuntimeException {
    public TransferCompanyMismatchException() {
        super("Requester must belong to the source account company");
    }
}
