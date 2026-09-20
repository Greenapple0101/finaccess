# 03. PostgreSQL을 독립적으로 실행하기

## 이번 단계의 질문

Spring 서버가 꺼져도 계좌와 거래 데이터는 어디에 남아 있을까요?

Spring 애플리케이션은 요청을 처리하고 업무 규칙을 실행합니다.
PostgreSQL은 애플리케이션과 별도 프로세스로 실행되어 데이터를 저장하고 SQL 요청에 응답합니다.
이번 단계에서는 DB만 실행합니다. Spring과의 연결, 업무 테이블 생성은 다음 단계입니다.

## compose.yaml 읽기

| 설정 | 의미 |
| --- | --- |
| `name: finaccess` | 이 프로젝트의 컨테이너·네트워크·볼륨을 묶는 이름 |
| `services.postgres` | 실행할 DB 서비스 |
| `image` | PostgreSQL 실행에 필요한 파일을 담은 공식 이미지; 버전을 고정해 기록 |
| `environment` | 최초 DB 이름·사용자·비밀번호 설정 |
| `ports` | 내 컴퓨터의 15432 포트를 컨테이너의 5432 포트에 연결 |
| `volumes` | DB 데이터 디렉터리를 Docker의 이름 있는 볼륨에 연결 |
| `healthcheck` | DB가 연결을 받을 준비가 되었는지 주기적으로 확인 |

`127.0.0.1`에 바인딩하므로 호스트에서는 로컬 주소로 접속합니다.
다른 프로젝트의 5432 포트와 충돌하지 않도록 15432를 기본으로 사용합니다.
`pg_isready`는 접속 준비 상태를 확인하며, 비밀번호 인증이나 업무 쿼리 성공까지 보장하지는 않습니다.

## 처음 실행하는 사람

저장소 루트에서 다음 명령을 실행합니다. 이미 `.env`가 있으면 덮어쓰지 않습니다.

```bash
cp -n .env.example .env
```

`.env`의 `POSTGRES_PASSWORD=`에 로컬에서 사용할 비밀번호를 입력한 뒤:

```bash
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

`.env`는 Git에서 제외됩니다. `.env.example`에는 비밀번호가 없는 설정 양식만 남깁니다.
Compose가 읽는 `.env`는 Spring Boot가 자동으로 읽는 파일이 아닙니다. Spring 연결 설정은 다음 단계에서 별도로 구성합니다.

## DB에 직접 질문하기

```bash
docker compose exec postgres psql -U finaccess -d finaccess
```

접속한 뒤 실행합니다.

```sql
SELECT current_database(), current_user;
SELECT 1 + 1 AS result;
```

`\q`를 입력하면 psql에서 나옵니다.
첫 쿼리는 접속한 DB와 사용자, 두 번째는 SQL 계산 결과 2를 보여줍니다.
컨테이너 안의 기본 로컬 소켓 접속은 비밀번호를 요구하지 않을 수 있습니다.
호스트에서 접속하는 DB 도구는 다음 정보를 사용합니다.

- Host: `localhost`
- Port: `15432` (변경했다면 `.env`의 값)
- Database / User: `finaccess`
- Password: 로컬 `.env`에 지정한 값

공식 이미지의 `POSTGRES_USER`는 초기 관리자 권한을 가진 사용자입니다.
이번 로컬 학습용 구성에만 사용하며, 배포 단계에서는 앱 전용 최소 권한 계정을 분리합니다.

## 컨테이너와 볼륨의 차이

컨테이너를 집의 건물, 볼륨을 별도로 보관하는 데이터 창고라고 생각해 보세요.
컨테이너를 새로 만들어도 같은 볼륨을 연결하면 데이터가 유지됩니다.

```bash
# 실행만 중지: 컨테이너와 데이터 유지
docker compose stop

# 기존 컨테이너 다시 시작
docker compose up -d --wait

# 컨테이너·네트워크 제거: 이름 있는 볼륨은 유지
docker compose down
```

`docker compose down -v`는 볼륨까지 삭제하여 DB 데이터를 잃게 합니다.
일상적인 종료에는 `-v`를 붙이지 않습니다. 볼륨이 유지되는 것과 백업은 다른 개념입니다.

## 비밀번호 설정이 바뀌지 않는 이유

공식 이미지의 초기 DB 설정 변수는 데이터 디렉터리가 비어 있는 첫 실행에 적용됩니다.
이미 초기화된 DB는 `.env`의 비밀번호만 바꿔도 DB 내부 비밀번호가 바뀌지 않습니다.
기존 데이터가 있다면 DB 사용자 비밀번호 변경 절차를 사용해야 합니다.

## 확인할 질문

- 내 컴퓨터는 왜 15432인데 컨테이너는 5432일까요?
- 컨테이너를 제거한 뒤에도 데이터가 남게 하는 설정은 무엇일까요?
- DB가 healthy이면 Spring 앱이 DB에 연결되었다는 뜻일까요?

## 참고

- PostgreSQL 공식 이미지: https://hub.docker.com/_/postgres
- Compose 준비 상태 확인: https://docs.docker.com/compose/how-tos/startup-order/

## 이번 단계의 검증 결과

- Compose 설정 검증과 DB healthcheck 통과.
- 비밀번호를 사용하는 컨테이너 내부 TCP 연결에서 SQL 실행 성공.
- 호스트의 15432 포트 접근 확인.
- 확인용 테이블에 데이터를 저장하고 컨테이너를 재생성한 뒤 같은 데이터 조회 성공.
- 검증용 테이블은 확인 후 제거했으며 업무 테이블은 아직 생성하지 않음.

Spring 연결 검증은 다음 단계에서 수행합니다.
