# 08. 회사 ID로 조회하기

## 이번 단계의 질문

잘못된 요청과, 올바른 요청이지만 데이터가 없는 상황은 어떻게 구분할까요?

| 상황 | 응답 |
| --- | --- |
| 올바른 UUID이며 회사가 존재 | 200 OK와 회사 정보 |
| 올바른 UUID이지만 회사가 없음 | 404 Not Found |
| UUID로 변환할 수 없는 경로 값 | 400 Bad Request |

## Controller: URL 값을 받기

```java
@GetMapping("/{id}")
public CompanyService.CompanyDetails findById(@PathVariable UUID id) {
    return companyService.findById(id);
}
```

클래스의 `/api/companies`와 합쳐 최종 경로는 `/api/companies/{id}`입니다.
`@PathVariable`은 경로의 값을 받습니다. 이전의 `@RequestBody`는 JSON 본문을 받았습니다.
Spring이 문자열을 UUID로 변환하므로 변환 불가능한 값은 Service 호출 전에 400으로 거부됩니다.

## Service: 데이터가 없는 경우를 명시하기

```java
@Transactional(readOnly = true)
public CompanyDetails findById(UUID id) {
    Company company = companyRepository.findById(id)
            .orElseThrow(() -> new CompanyNotFoundException(id));
    return new CompanyDetails(company.getId(), company.getName(), company.getCreatedAt());
}
```

`findById`는 Optional을 반환합니다. 데이터가 없을 가능성을 타입으로 드러내는 것입니다.
`orElseThrow`는 값이 있으면 꺼내고, 없으면 지정한 예외를 발생시킵니다.
Service는 HTTP 코드를 직접 정하지 않습니다. CompanyNotFoundException을 Controller가
ProblemDetail 형식의 404 응답으로 바꿉니다.

readOnly는 조회용 트랜잭션이라는 힌트이며 DB·JPA 구현에 따라 최적화 등에 활용됩니다.
쓰기를 금지하는 보안 수단이나 접근 권한 검사가 아닙니다.

## 생성 시각

등록 응답에는 id와 name만 있었지만 조회 응답에는 createdAt도 있습니다.
이번에는 DB에서 다시 읽기 때문에 DB가 채운 생성 시각도 얻을 수 있습니다.
엔티티를 직접 반환하지 않고 CompanyDetails라는 응답용 데이터를 만듭니다.

## 직접 확인하기

앱 실행:

```bash
docker compose up -d --wait
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'
```

회사 등록:

```bash
curl -i -X POST http://localhost:8081/api/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"조회 연습 회사"}'
```

응답의 id를 복사하여 `등록응답의UUID`를 바꿉니다.

```bash
curl -i http://localhost:8081/api/companies/등록응답의UUID
curl -i http://localhost:8081/api/companies/not-a-uuid
```

없는 회사 확인은 DB에 존재하지 않는 올바른 UUID로 요청합니다.
404 응답의 detail에는 요청한 ID가 포함됩니다.
아직 회사별 접근 권한은 없으며 로컬 학습용입니다. 소속·인증 구현 단계에서 권한 검사를 추가합니다.

## 검증

실제 PostgreSQL에 회사 저장을 커밋한 뒤 MockMvc로 조회합니다.
응답의 id·name과 createdAt을 확인하고, createdAt이 DB 저장값과 일치하는지도 검사합니다.
없는 회사의 404와 잘못된 UUID의 400을 별도 테스트합니다.

## 생각해 볼 질문

- 회사가 없을 때 200과 빈 객체를 보내는 것과 404를 보내는 것은 어떻게 다를까요?
- Service가 HTTP 응답 대신 회사 없음 예외를 던지면 어떤 장점이 있을까요?
