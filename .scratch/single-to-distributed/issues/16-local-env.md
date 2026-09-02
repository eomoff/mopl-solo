# 로컬 개발·분산 환경 구성

Type: grilling
Status: resolved

## Question

[분산 전환 이음매 확정]에서 시청 세션을 **처음부터 Redis**에 두기로 하면서 Redis가 1일차 의존성이 됐다. 개발·테스트·전환 실습 환경을 어떻게 구성할지가 이제 결정 가능하다.

- **1일차에 무엇을 띄우는가** — PostgreSQL + Redis는 확정. Kafka는 [이벤트 발행이 인터페이스 뒤에 있으므로] 전환 시점까지 미룰 수 있다. 처음부터 띄울 것인가, 나중에 추가할 것인가.
- **프로파일 구성** — `info.md`가 환경별 구성을 요구한다. 로컬 단일 / 로컬 분산 / 운영을 어떤 축으로 나눌 것인가. **이음매의 구현체 선택이 프로파일로 갈리는가**, 아니면 다른 방식인가.
- **로컬 분산 환경의 형태** — 전환을 실습하려면 인스턴스 2대 + Nginx가 필요하다. Docker Compose로 어떻게 구성할 것인가. 앱을 이미지로 말아서 2개 띄우는가, 포트만 다르게 2개 실행하는가.
- **개발 중 프론트엔드** — `src/main/resources/static/`의 빌드 산출물로 볼 것인가, `pnpm dev`(프록시 설정 이미 있음)로 띄울 것인가. 분산 환경에서는 Nginx가 정적 파일을 서빙하는가 앱이 서빙하는가.
- **애플리케이션 설정** — `application.yaml`이 사실상 비어 있다(`spring.application.name`뿐). 연결 정보와 비밀을 어떻게 주입할 것인가.

---

**[실시간 WebSocket 아키텍처 설계]가 넘긴 것**

- **Nginx의 SSE 버퍼링을 꺼야 한다.** 켜져 있으면 알림이 뭉쳐서 오거나 아예 도달하지 않는다. 분산 단계에서 Nginx를 앞에 세우는 순간 걸리는 문제다.
- **SockJS와 WebSocket 업그레이드**도 Nginx 설정에 걸린다. 프론트가 raw WebSocket이 아니라 SockJS를 쓰므로 폴백 트랜스포트(xhr-streaming 등)까지 통과해야 한다.


## Answer

> **결정 주체**: Q3은 사용자 선택(에이전트 추천과 일치). Q1·Q2·Q4~Q7은 에이전트가 추천하고 사용자가 승인했다.

### 출발점 사실

- `spring-boot-testcontainers`가 이미 의존성에 있다 — `@ServiceConnection` 방식을 쓸 준비가 됐다.
- **Testcontainers 모듈은 PostgreSQL뿐이다.** Redis가 1일차 의존성이 됐는데 없다.
- **`spring-boot-docker-compose`가 없다** (Q2의 대상).
- `application.yaml`은 `spring.application.name` 뿐이고 프로파일이 하나도 없다.

### Q1. Kafka는 1일차에 띄우지 않는다 — **에이전트 추천 / 사용자 승인**

PostgreSQL + Redis만 띄운다. 이벤트 발행이 인터페이스 뒤에 있고 단일 구현은 `ApplicationEvent`라, Kafka를 켜둬도 **쓰지 않는 컨테이너가 메모리만 먹는다.**

**Docker Compose 프로파일로 분리한다.**

```
docker compose up                        # postgres, redis
docker compose --profile distributed up  # + kafka, nginx, app×2
```

이러면 **"전환한다"는 행위가 명령어 한 줄의 차이로 드러나고**, 전환 전후를 오가며 비교하기 쉽다.

### Q2. `spring-boot-docker-compose`를 넣는다 — **에이전트 추천 / 사용자 승인**

`developmentOnly` 스코프. `bootRun` 시 Compose가 자동 기동되고 연결 정보가 주입되므로 "docker compose up 했던가?"라는 마찰과 접속 정보 하드코딩이 함께 사라진다.

**단일 개발 모드 전용이다** — 분산 모드에서는 앱 자체가 컨테이너 안이라 이 모듈이 관여할 자리가 없다.

### Q3. 이음매는 프로파일이 아니라 **설정 프로퍼티**로 가른다 — **(b), 사용자 선택 (에이전트 추천과 일치)**

`mopl.<축>.mode` 형태의 축별 스위치 + `@ConditionalOnProperty`로 구현체를 고른다.

**프로파일을 버린 이유가 이 연습의 핵심에 닿는다.** 프로파일로 가르면 `distributed`를 켜는 순간 **네 이음매가 동시에 바뀐다.** 뭔가 깨졌을 때 전파·이벤트 발행·파일 저장·캐시 중 무엇 때문인지 알 수 없다.

프로퍼티로 가르면 **하나씩 밟을 수 있다.** 전파만 먼저 바꿔 "채팅이 두 인스턴스에서 보이는가"를 확인하고, 그다음 이벤트 발행을 Kafka로 바꾸고… **한 번에 하나씩 검증하는 것**이 "전환을 겪어본다"는 목적지에 부합한다.

**프로파일은 환경(local/prod)을 가르는 데만 쓴다.** 두 축을 섞으면 `local-single`·`local-distributed`·`prod-distributed`로 조합이 터진다.

### Q4. 로컬 분산은 이미지 2개 + Nginx, 단 `bootRun` 모드를 버리지 않는다 — **에이전트 추천 / 사용자 승인**

AWS ECS가 목적지이므로 **Dockerfile은 어차피 필요하다.** 로컬 분산에서 같은 이미지를 쓰면 "로컬에선 되는데 ECS에선 안 됨"이 줄어든다.

그러나 **개발 반복 중에는 `bootRun` 단일이 압도적으로 빠르다.** 코드 한 줄 고칠 때마다 이미지를 다시 마는 것은 견딜 수 없다. **평소엔 `bootRun` 단일, 전환 검증 시에만 이미지 2개** — 두 모드를 모두 유지한다.

### Q5. 분산에서도 정적 파일은 앱이 서빙한다 — **에이전트 추천 / 사용자 승인**

정적 파일이 jar 안에 있어 Nginx로 옮기려면 **빌드 산출물을 두 곳에 배포**해야 한다. 그리고 Nginx 설정은 이미 까다롭다 — **SSE 버퍼링 끄기**와 **SockJS 폴백 트랜스포트 통과**를 먼저 맞춰야 한다. 정적 파일 최적화는 지도의 Out of scope("AWS 상시 운용·비용 최적화")에 가깝다.

개발 중 `pnpm dev`도 열려 있다 — `vite.config.ts`에 `/api`·`/oauth2`·`/ws` 프록시가 이미 설정되어 있다.

### Q6. 설정은 프로파일 파일, 비밀은 전부 환경변수 — **에이전트 추천 / 사용자 승인**

`application.yaml`(공통) + `application-{profile}.yaml`(환경별). 비밀(TMDB 토큰, DB 비밀번호, JWT 서명 키)은 환경변수로 주입하고, 로컬은 `.env`에 두고 `spring.config.import=optional:file:.env[.properties]`로 읽는다.

**이 티켓에서 실제로 조치한 것**: `.gitignore`에 `.env` 규칙이 **없었다.** 저장소가 public이므로 곧바로 사고가 될 수 있어 `.env`, `.env.*`(단 `.env.example`은 예외)를 추가했다.

### Q7. 테스트 Redis는 `GenericContainer` + `@ServiceConnection("redis")` — **에이전트 추천 / 사용자 승인**

Redis는 컨테이너 설정이 단순해 별도 모듈 없이 충분하다.

**⚠️ 확인하지 않은 것**: Spring Boot 4.1에서 `@ServiceConnection`에 연결 이름을 주는 방식이 Redis에 그대로 동작하는지 **실제로 돌려보지 않았다.** 안 되면 Testcontainers Redis 모듈을 추가하면 되므로 위험은 낮지만, 추측으로 확정하지 않고 표시해 둔다.

"단위 테스트마다 Redis가 필요한가"는 [이음매를 감싸는 테스트 전략]의 문제이므로 여기서 답하지 않는다.
