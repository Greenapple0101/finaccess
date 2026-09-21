# SQL을 주석으로 읽기

이미 적용한 Flyway SQL 파일은 주석 변경도 체크섬에 영향을 줄 수 있습니다.
따라서 원본 V1·V2는 그대로 두고 아래에 해설용 사본을 제공합니다.
아래 SQL을 별도로 실행할 필요는 없습니다. 앱 시작 시 Flyway가 원본을 적용합니다.

## V1: 회사

```sql
-- CREATE TABLE은 새 테이블을 정의합니다.
CREATE TABLE companies (
    -- UUID 식별자. PRIMARY KEY는 중복과 NULL을 허용하지 않습니다.
    id UUID PRIMARY KEY,
    -- VARCHAR(100)은 최대 100자 문자열입니다.
    -- NOT NULL은 값 누락을 금지합니다. CHECK는 공백 제거 후 길이가 0보다 큰지 검사합니다.
    name VARCHAR(100) NOT NULL CHECK (char_length(trim(name)) > 0),
    -- 시간을 생략한 INSERT라면 DB가 현재 시각을 넣습니다.
    -- TIMESTAMPTZ는 시점을 저장하며 조회 시 세션 시간대에 맞추어 표시합니다.
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## V2: 업무 사용자

```sql
CREATE TABLE app_users (
    -- FinAccess 내부에서 사용자를 구분하는 ID입니다.
    id UUID PRIMARY KEY,
    -- 외래키: companies 테이블에 실제 존재하는 id만 넣을 수 있습니다.
    -- 사용자 행 하나에 company_id 하나이므로 현재 모델은 한 회사 소속입니다.
    company_id UUID NOT NULL REFERENCES companies (id),
    -- 인증 발급자: 향후 검증된 토큰의 iss와 연결합니다.
    identity_issuer VARCHAR(512) NOT NULL CHECK (char_length(trim(identity_issuer)) > 0),
    -- 발급자 안의 사용자 식별자: 향후 검증된 토큰의 sub와 연결합니다.
    identity_subject VARCHAR(255) NOT NULL CHECK (char_length(trim(identity_subject)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 두 값을 묶은 조합이 유일해야 합니다. 각각을 단독으로 UNIQUE 처리한 것은 아닙니다.
    CONSTRAINT uk_app_users_identity UNIQUE (identity_issuer, identity_subject)
);

-- 회사 소속으로 사용자를 찾는 작업을 돕는 색인입니다.
-- 색인은 조회를 돕지만 저장 공간과 쓰기 작업 비용도 발생합니다.
CREATE INDEX idx_app_users_company_id ON app_users (company_id);
```

외래키는 데이터 관계를 보호하며, 요청자가 그 회사에 접근할 권한이 있는지는 판단하지 않습니다.
그 검사는 이후 Spring 인증·인가 계층에서 구현합니다.
