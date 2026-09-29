// [이 파일은 무엇을 하나요?]
// 행동하는 사용자의 회사와 출금 계좌 회사가 다르다는 오류에 이름을 붙입니다.
// 예: B회사 직원이 A회사 계좌의 이체를 요청·결재·실행하려 하면 Service가 이 예외를 알립니다.
// 흐름: 회사 비교 실패 → 이 예외 발생 → Controller가 403 응답으로 변환.
// 회사 소속 확인은 로그인한 본인인지 확인하는 일과 다릅니다. 로그인 연결은 아직 없습니다.
package io.github.greenapple0101.finaccess.transfer;

// RuntimeException은 실행 도중 작업을 중단시킬 수 있는 예외의 한 종류입니다.
// Spring 트랜잭션 밖으로 전달되면 기본 설정상 그 작업의 DB 변경이 취소됩니다.
public class TransferCompanyMismatchException extends RuntimeException {
    public TransferCompanyMismatchException() {
        // super는 부모인 RuntimeException에 오류 설명을 전달합니다.
        // 설명의 requester라는 표현은 현재 이 예외를 함께 쓰는 결재자·실행자에도 적용됩니다.
        super("Requester must belong to the source account company");
    }
}
