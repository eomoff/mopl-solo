# mopl-solo — 모두의 플리

영화·드라마·스포츠 콘텐츠를 큐레이팅해 공유하고, 평점을 남기고, 같은 콘텐츠를 보는 사람끼리 실시간으로 어울리는 소셜 서비스.

<!-- 커버리지 배지는 CI 워크플로가 만들어진 뒤 여기에 붙는다 (Gist + shields.io endpoint) -->

## 이 저장소는 무엇인가

**단일 인스턴스로 만든 서비스를 분산 환경으로 전환해보는 개인 연습**입니다. 기능을 완성하는 것이 목적이 아니라, **인스턴스가 한 대에서 두 대로 늘어날 때 무엇이 깨지는가**를 직접 겪어보는 것이 목적입니다.

그래서 설계가 조금 특이합니다. 예를 들어 WebSocket 브로커는 **의도적으로 단일 인스턴스용 구현으로 시작합니다.** 분산 환경에서 채팅이 반쪽만 보이는 것을 직접 확인한 뒤에 갈아끼우기 위해서입니다. 그런 결정과 근거는 전부 [설계 지도](#설계-문서)에 기록되어 있습니다.

## 현재 상태

**설계 완료, 구현 시작 전.**

| | |
| --- | --- |
| 설계 결정 | 18건 완료 ([지도](.scratch/single-to-distributed/map.md)) |
| 구현 | 미착수 — 도메인 패키지 스켈레톤과 API 계약만 있음 |
| 기간 | 12주 계획 |

## 목표 — 단일에서 분산으로

```
1단계  단일 인스턴스        모든 상태가 한 프로세스 안에 있다
2단계  로컬 분산            앱 2개 + Nginx, 무엇이 깨지는지 확인
3단계  AWS                  ECS에 올려 실제 분산 환경에서 검증
```

전환은 한 번에 일어나지 않습니다. **이음매마다 설정 프로퍼티 스위치**(`mopl.<축>.mode`)가 있어 하나씩 바꿔가며 검증합니다 — 전파 → 이벤트 발행 → 파일 저장 → 캐시.

**전환의 완료는 테스트가 판정합니다.** "단일 모드에서는 다른 인스턴스의 구독자가 메시지를 받지 못한다"는 지금 **통과하는** 테스트이고, 전환이 성공하면 이 테스트가 실패로 바뀝니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어·프레임워크 | Java 21, Spring Boot 3.5.16 |
| 영속성 | PostgreSQL, Spring Data JPA |
| 인증 | Spring Security, JWT (jjwt), OAuth2 (Google, Kakao) |
| 실시간 | WebSocket (STOMP + SockJS), SSE |
| 배치 | Spring Batch, Actuator |
| 캐시·메시징 | Redis, Kafka |
| 인프라 | Docker Compose, Nginx, AWS ECS |
| 테스트 | JUnit 5, Testcontainers, JaCoCo (라인 커버리지 80%) |
| 외부 API | TMDB, TheSportsDB |

## 설계 문서

| 문서 | 내용 |
| --- | --- |
| [`.scratch/single-to-distributed/map.md`](.scratch/single-to-distributed/map.md) | **설계 지도** — 목적지, 결정 목록, 범위 밖으로 밀어낸 것 |
| [`.scratch/single-to-distributed/issues/`](.scratch/single-to-distributed/issues/) | 결정 티켓 18건 — 질문, 답, 그리고 **그렇게 정한 이유** |
| [`.scratch/single-to-distributed/research/`](.scratch/single-to-distributed/research/) | 외부 API 조사 — 출처 URL과 실측 결과 |
| [`CONTEXT.md`](CONTEXT.md) | 도메인 용어집 |
| [`contract/`](contract/) | **API 계약**(OpenAPI) — 이 서버가 만족시켜야 할 외부 제약 |

계약과 문서가 어긋나면 **계약이 이깁니다.** 알려진 계약 결함은 [`contract/README.md`](contract/README.md)에 기록되어 있습니다.

## 12주 계획

| 주 | 내용 | 확인 지점 |
| --- | --- | --- |
| 1 | 세팅, 인증 착수 | CI가 초록으로 돈다 |
| 2 | 인증 완성 | 브라우저에서 로그인이 된다 |
| 3 | 콘텐츠 + 수집 배치 | 콘텐츠 목록이 뜬다 |
| 4 | **AWS 1차 배포(단일)** | 파이프라인이 동작한다 |
| 5 | 실시간 (단일 구현) | 탭 두 개로 채팅이 보인다 |
| 6 | **첫 전환 — 전파 이음매** | 알람 테스트가 뒤집힌다 |
| 7 | **AWS 2차 배포(분산)** | 두 인스턴스에서 채팅이 보인다 |
| 8 | 팔로우·구독·DM | 관계 기능이 돈다 |
| 9 | 알림 (SSE) | 알림이 두 인스턴스에서 온다 |
| 10 | 나머지 전환 (Kafka·S3·Redis) | 알람 테스트 3개가 더 뒤집힌다 |
| 11–12 | 버퍼, 마무리 | |

## 실행 방법

> 아직 구현 전이라 아래는 **1주차에 갖춰질 형태**입니다.

### 환경변수

```bash
MOPL_TMDB_ACCESS_TOKEN   # TMDB API Read Access Token (v4, eyJ... 형태)
MOPL_JWT_SECRET          # JWT 서명 키
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KAKAO_CLIENT_ID
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KAKAO_CLIENT_SECRET
```

로컬 DB·Redis 접속 정보는 `spring-boot-docker-compose`가 자동 주입합니다. TheSportsDB는 공개 무료 키를 쓰므로 설정이 필요 없습니다.

### 단일 인스턴스 (평소 개발)

```bash
./gradlew bootRun          # Compose(postgres, redis)가 함께 뜬다
```

브라우저에서 `http://localhost:8080` — 프론트엔드는 `src/main/resources/static/`에서 같은 오리진으로 서빙됩니다.

### 로컬 분산 (전환 검증)

```bash
docker compose --profile distributed up    # + kafka, nginx, app×2
```

### 테스트

```bash
./gradlew build            # 테스트 + 커버리지 게이트(80%)
```

Testcontainers로 PostgreSQL·Redis를 실제로 띄웁니다. 외부 API를 호출하는 테스트는 `@Tag("external")`로 분리되어 기본 실행에서 제외됩니다.

## 프론트엔드

`src/main/resources/static/`의 프론트엔드는 **코드잇이 제공한 산출물**(`project-mopl-fe-1.0.5`)이며 이 저장소 작성자의 작업물이 아닙니다. 이 프로젝트의 범위는 백엔드입니다.

## 데이터 출처

This product uses the TMDB API but is not endorsed or certified by TMDB.

<!-- TMDB 로고를 https://www.themoviedb.org/about/logos-attribution 에서 받아 이 절에 추가할 것 -->

스포츠 경기 데이터는 [TheSportsDB](https://www.thesportsdb.com/)에서 가져옵니다.
