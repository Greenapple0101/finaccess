// [회사 데이터를 저장하고 찾는 통로]
// Service가 companyRepository.save(회사)나 findById(회사ID)를 호출할 때 사용합니다.
// 호출 흐름: CompanyService → 이 저장소의 구현 객체 → JPA → DB.
// 저장소(Repository)는 데이터 접근을 맡고, HTTP 응답이나 접근 권한은 결정하지 않습니다.
//
// interface는 "어떤 기능을 사용할 수 있는지" 적는 약속입니다.
// Spring Data JPA가 이 약속에 맞는 동작을 준비해서 Service에 전달합니다.
// 따라서 우리가 모든 저장·조회 코드를 직접 구현하지 않아도 됩니다.

package io.github.greenapple0101.finaccess.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// JpaRepository의 기본 기능을 물려받습니다. Company는 저장할 대상, UUID는 그 대상의 ID 타입입니다.
// save는 저장을 요청하는 메서드입니다. 호출한 그 순간에 DB 변경이 확정된다는 뜻은 아닙니다.
// 변경 확정은 commit, SQL을 DB에 보내는 처리는 flush라고 하며 서로 다른 단계입니다.
// findById는 조회 결과를 Optional로 돌려줍니다. "회사 하나가 있거나 없을 수 있다"는 뜻입니다.
// 값이 없을 때 어떻게 할지는 CompanyService에서 결정합니다.
public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
