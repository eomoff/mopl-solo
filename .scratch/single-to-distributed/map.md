# 단일 → 분산 전환 연습 설계도

Label: `wayfinder:map`

## Destination

`docs/info.md`의 요구사항을 **단일 인스턴스로 구현한 뒤 분산 환경으로 전환해 AWS까지 올리는 12주 개인 연습**을, "이제 만들기만 하면 되는" 상태로 만든다.

과거 팀 결과물을 참조하지 않고 백지에서 다시 내리는 **설계 결정 집합**이 이 지도의 산출물이다. 구현은 지도 밖에서 진행한다.

## Notes

**도메인**: 영화·드라마·스포츠 콘텐츠를 큐레이팅·평가하고, 같은 콘텐츠를 보는 사람끼리 실시간으로 어울리는 소셜 서비스. Spring Boot 4.1.1 / Java 21 / PostgreSQL + JPA / Redis / Kafka.

**상시 제약**

- **과거 프로젝트 완전 봉인** — 동일 요구사항의 팀 구현체와 그 파생 문서(ERD·API 정리본·컨벤션·회고)를 일절 열지 않고, 근거로도 쓰지 않는다. 백지에서 다시 결정하는 것이 이 연습의 목적이다.
- **API 계약의 소스 오브 트루스**는 저장소 루트 `project-mopl-fe-1.0.5.zip` 안의 `api.json`(OpenAPI)과 `src/lib/types/api.ts`. DTO 필드와 상태코드는 설계 대상이 아니라 따라야 할 외부 제약이다.
- **TDD 유지** — Red-Green-Refactor, jacoco LINE 커버리지 80% 게이트(`build.gradle`에 배선 완료). 전환 이음매를 감싸는 테스트가 있어야 갈아끼우기가 안전하다.
- **프론트엔드 실제 연동** — `project-mopl-fe-1.0.5-release.zip`의 `dist/`를 서빙해 브라우저로 검증한다. 실시간 기능은 눈으로 봐야 맞는지 안다.
- **AWS는 검증할 때만 올리고 내린다** — 상시 과금 회피. 따라서 "내렸다 올려도 상태가 보존되는가"가 결정 대상이 된다.

**확정된 배치** (자세한 근거는 [프론트엔드 계약 확보와 1.0.5 차이 확인])

- `contract/` — `api.json`·`api.ts`·`README.md`. **커밋됨.** 계약의 소스 오브 트루스.
- `frontend/` — 제공 번들 원본과 zip. **git 미추적.**
- `src/main/resources/static/` — 빌드된 프론트. Spring이 같은 오리진으로 서빙하므로 CORS 설정이 필요 없다.

**용어집**: 저장소 루트 [`CONTEXT.md`](../../CONTEXT.md). 용어가 흔들리면 먼저 여기를 본다.

**매 세션 참조 스킬**: `grilling`, `domain-modeling`. 모듈 경계·인터페이스를 다룰 땐 `codebase-design`.

**트래커**: 로컬 마크다운. 이 디렉토리가 곧 트래커이며, **git으로 추적된다**(`origin`: https://github.com/eomoff/mopl-solo).

## Decisions so far

- [TMDB API 조사](issues/02-tmdb-research.md): 증분 수집 가능 — 일일 ID 익스포트(존재)와 `/changes`(변경) 두 축을 병행하고 델타만 상세 조회. **`/discover`는 500페이지 상한이라 전체 훑기 불가.** 한국어 텍스트 폴백 없음(앱이 직접 체인 구성), `tmdbId`+`mediaType` unique 제약 필수. 전문: [`research/tmdb-api.md`](research/tmdb-api.md)
- [The Sports DB API 조사](issues/03-sportsdb-research.md): **증분 수집 수단이 없고**(`idEvent` upsert가 유일), 무료 키로는 배치 불가(전 세계 하루 3경기) — 실질 최소선 **$9/월**. 프론트 계약이 `type: movie|tvSeries|sport` 단일 `Content`를 이미 강제하지만 **날짜 필드가 없어** 경기 시작 시각을 놓을 자리가 없다. 429가 JSON이 아닌 Cloudflare 텍스트다. 문서가 실제와 어긋나 120회 실측으로 검증. 전문: [`research/sportsdb-api.md`](research/sportsdb-api.md)
- [프론트엔드 계약 확보와 1.0.5 차이 확인](issues/01-frontend-contract.md): 계약 사본을 `contract/`에 커밋, 번들 원본은 `frontend/`(미추적), `dist/`는 `src/main/resources/static/`으로. **CORS·SPA 폴백 모두 불필요** — 프론트가 상대 경로를 쓰고 HashRouter라서. 경로 32 / 오퍼레이션 45 / 스키마 37. **`watchingsession`·`directmessage`에는 독립 쓰기 경로가 없고**, 소셜 로그인과 WebSocket/SSE 페이로드는 계약 밖이다.
- [스포츠 콘텐츠를 목적지에 둘 것인가](issues/14-sports-scope.md): **(ㄴ) 무료 티어로 축소 포함.** 스포츠는 "타입이 여러 개인 콘텐츠 모델"이라는 설계 문제를 제공하는 선에서 존재하고, 배치의 학습 가치는 TMDB 규모에서 확보한다. 유료 구독 없음.
- [GitHub 저장소 개설](issues/11-github-repo.md): **https://github.com/eomoff/mopl-solo** (public). 지도(`.scratch/`)를 **git으로 추적**하기로 하고 `.gitignore`에서 뺐다. `frontend/`·`.DS_Store` 무시 추가. `docs/`는 무시 유지 — `info.md`는 공개되지 않는다.
- [분산 전환 이음매 확정](issues/04-distribution-seams.md): 시청 세션은 **처음부터 Redis**(전환 없음, Redis가 1일차 의존성). WebSocket 브로커는 **의도적으로 단일 인스턴스용으로 남겨** 거기서 전환을 겪는다. **브로커·SSE 전파는 하나의 이음매**로 통합. 이벤트 발행·파일 저장·캐시는 인터페이스 뒤, 배치 잠금은 전환 시점에. 되돌리는 비용이 가장 높은 것은 **이벤트 발행**. **(2026-09-03 수정: 시청 세션 집계값은 DB 비정규화 컬럼으로)**
- [도메인 모델 확정](issues/05-domain-model.md): PK는 UUID·시간은 `date-time`(계약이 고정). **정렬 축인 계산 필드만 저장**(`averageRating`·`watcherCount`·`subscriberCount`), 조회자별 값(`subscribedByMe`·`hasUnread`)은 파생. 타입별 속성은 **부속 테이블**, 태그는 별도 테이블. `auth`는 엔티티 없이 시작. 계약 결함 3건은 글자 그대로 따르되 **DTO 경계에서만 매핑**한다. 용어집은 [`CONTEXT.md`](../../CONTEXT.md).
- [실시간 WebSocket 아키텍처 설계](issues/07-realtime.md): **STOMP + SockJS**(`/ws`), 인증은 `CONNECT` 프레임 헤더. **클라이언트가 JOIN/LEAVE를 발행하지 않으므로 서버가 구독 이벤트에서 파생**한다. 시청 세션은 사용자 기준 + 참조 카운트. **DM 두 경로는 중복이 아니라 상보적** — 서버는 둘 다 보낸다. 대화 토픽은 `ChannelInterceptor`로 참여자 인가 검사(없으면 남의 DM이 새어나간다). *(에이전트 판단으로 확정)*
- [알림·이벤트 파이프라인 설계](issues/08-notification.md): **읽음 처리가 곧 삭제**이고 알림에 타겟 링크가 없다(순수 텍스트). **시청 알림을 만들지 않기로 해 트리거가 6종→5종**으로 줄었다(요구사항의 의도적 축소). 팬아웃은 쓰기 시점, 발행은 `DomainEventPublisher` 단일 진입점 + `AFTER_COMMIT`. `ERROR` level은 쓰지 않는다.
- [인증·인가와 토큰 무효화 설계](issues/06-auth.md): 액세스 토큰은 **메모리에만** 있고 `refresh`로 복구된다(재발급 트리거는 **401**). 리프레시 토큰을 **저장·회전**하고 **동시 로그인을 막는다**(액세스 15분/리프레시 14일). 즉시 반영은 **Redis 무효화 목록**(TTL=15분). CSRF는 형식이 아니다 — `refresh`·`sign-out`이 쿠키만으로 인증된다. **사용자 탈퇴는 구현하지 않는다.** `auth` 패키지에 리프레시 토큰·소셜 계정 연결 두 엔티티가 생겼다.
- [콘텐츠 수집 배치 설계](issues/09-batch.md): 백필은 TMDB **`popularity` 상위 3만 건**(전량 124만은 17시간이라 개발 반복이 불가능). Job은 **소스별 분리** + 유지보수 Job 하나. 오류는 **429/404/5xx 세 갈래**로 가르고 `Retry-After`를 따른다(SportsDB 429는 **평문**이라 상태코드 먼저 분기). 워터마크·묘비 테이블 추가, 한국어 폴백은 **수집 시점 적용**.
- [로컬 개발·분산 환경 구성](issues/16-local-env.md): **이음매는 프로파일이 아니라 설정 프로퍼티(`mopl.<축>.mode`)로 가른다** — 프로파일이면 네 이음매가 동시에 바뀌어 무엇이 깨졌는지 알 수 없다. 1일차는 Postgres+Redis만, Kafka·Nginx·app×2는 Compose `distributed` 프로파일로. 평소엔 `bootRun` 단일, 전환 검증 때만 이미지 2개. 정적 파일은 분산에서도 앱이 서빙한다. `.gitignore`에 `.env` 추가(public 저장소).
- [이음매를 감싸는 테스트 전략](issues/15-seam-tests.md): **깨짐을 실패가 아니라 통과로 표현한다** — "단일 모드에서는 전파되지 않는다"가 통과하는 테스트이고, 전환 시 이것이 실패로 바뀌는 것이 **전환 완료의 판정 기준**이 된다. 추상 계약 테스트 + 구현별 하위 클래스, 인스턴스 2대는 같은 JVM에 컨텍스트 2개(**단일 구현이 `static`이면 거짓 통과가 난다**). 인프라는 진짜, 외부 API는 가짜. `jacocoTestCoverageVerification`이 `check`에 미연결이다.
- [브랜치 전략과 CI/CD 결정](issues/12-ci-cd.md): 기능 브랜치 + PR, **승인 요구 없이 필수 상태 검사만** (혼자인데 승인을 요구하면 매번 우회하게 된다). **이음매 전환마다 PR 하나** = 되돌릴 수 있는 단위. CI는 `./gradlew build` 하나로 시작해 전환 시점에 단일/분산 job으로 분리. 배지는 Gist + shields.io, 배포는 **`workflow_dispatch` 수동 + OIDC**(장기 키를 시크릿에 넣지 않는다). 80% 게이트는 처음부터 켜되 `dto`·`config`·`MoplApplication` 제외.
- [12주 마일스톤 구획](issues/13-milestones.md): **세로로 얇게 뚫고 넓힌다**(프론트가 있어 즉시 확인된다). **기능이 절반쯤일 때 첫 전환**(6주차 전파) — 늦추면 전환이 마지막 2주에 몰려 터진다. **AWS는 두 번 올리고 1차는 단일 인스턴스**다(실패 시 배포 문제와 분산 문제가 섞이지 않게). 3주차에 **스포츠 Job을 켠다**(켜둔 날수가 곧 데이터). 마지막 2주는 버퍼.
- [예외·에러 응답 계약 확정](issues/17-error-contract.md): 계약이 쓰는 코드는 **400/401/403/404/500 다섯 개뿐이고 409가 없다** — 중복도 400, **404 미선언 조회의 없는 리소스도 400**(계약이 이긴다). **프론트는 서버 `message`를 한 번도 표시하지 않으므로** `message`·`details`는 개발자용으로 최적화한다. `exceptionName`에는 도메인 에러 코드. **401/403을 섞으면 무한 재발급 루프**가 되므로 `AuthenticationEntryPoint`로 미인증을 반드시 401로 만든다.
- [AWS 배포 형상 확정](issues/18-aws-topology.md): **프라이빗 서브넷 + NAT**(퍼블릭엔 Nginx만), RDS는 **스냅샷 후 삭제·복원**, **Redis는 ECS 태스크**(담긴 것이 전부 휘발 가능하므로 ElastiCache 불필요 — `info.md`에서 의도적으로 벗어난 지점). ALB 없이 **Nginx가 앞에 서고 앱은 desired count 2**. 이미지 태그는 커밋 SHA. **teardown도 워크플로로 만든다** — 손으로 내리면 NAT Gateway를 빠뜨린다.
- [외부 API 키 발급](issues/10-api-keys.md): TMDB **Read Access Token**을 `MOPL_TMDB_ACCESS_TOKEN`으로 주입(확보 완료). TheSportsDB는 공개 키 `123`이라 설정 불필요. **TMDB 귀속 표기가 의무**이고 SLA가 없다. 한국어 폴백의 실제 동작은 **1주차에 `curl` 한 줄로 확인해 [콘텐츠 수집 배치 설계]에 기록**한다.
- [캐시 전략 확정](issues/19-cache.md): 캐시는 성능이 아니라 **이음매로만 남는다** — 인메모리로 3주차에 넣고 10주차에 Redis로 전환한다. 계약이 required로 박은 **`watcherCount`가 설계를 정했다**(통째 캐시하면 evict가 실시간으로 터져 히트율 0) — 빼고 담고 조립 시점에 붙인다. 콘텐츠 **단건만**, **TTL 없이 evict만**, 캐시 전용 값 타입, evict는 평점 재계산 지점에(도메인 이벤트에 태우면 10주차에 두 이음매가 동시에 움직인다).
- [어드민 계정 초기화 규칙](issues/21-admin-bootstrap.md): 이메일·이름은 설정, **비밀번호만 환경변수**(없으면 **기동 실패**). **없을 때만 만들고**, 경합은 `users(email)` unique에 맡긴다 — **이음매가 아니다**(전환할 것이 없다). **자기 자신의 `role`·`locked` 변경을 403으로 막아 어드민 0명을 구조적으로 불가능하게** 한다. "있으면 승격"은 **가입 한 번으로 관리자가 되는 권한 상승 취약점**이라 배제. Flyway를 들이지 않는다.
- [스키마 관리 방식 확정](issues/22-schema-management.md): **Flyway를 1주차부터.** 4주차 첫 배포 전까지는 마이그레이션 파일을 고쳐 써도 되고 그 뒤로 append-only — **append-only 규율은 배포된 뒤에야 필요하다.** 테스트도 Flyway로 돌린다(**스키마도 인프라**, ddl-auto면 마이그레이션 오타를 CI가 못 잡는다) + `validate` 병행. Spring Batch 메타 테이블까지 Flyway가 소유. **forward-only**이고 파괴적 변경은 두 단계로 나눈다. `V1`에 전체를 넣지 않고 **주차마다 필요한 것만** — 마이그레이션이 마일스톤의 리듬을 갖는다.

## Not yet specified

*(현재 없음.)* 스키마 관리 방식이 [스키마 관리 방식 확정]으로 승격됐다. 남은 티켓이 닫히는 과정에서 새 안개가 걷히면 여기에 다시 적는다.

## Out of scope

- **Elasticsearch 검색 리팩토링** — 검색 품질 개선은 "분산 전환을 겪어본다"는 이번 목적지와 축이 다르다. `build.gradle`에 의존성은 남겨두되 이번 지도에서는 다루지 않는다.
- **AWS 상시 운용·비용 최적화** — 검증 시에만 올렸다 내리므로, 상시 운영을 전제로 한 결정(오토스케일링 정책, 비용 튜닝)은 목적지 밖이다.
- **구현 자체** — 이 지도는 결정만 산출한다. 12주의 구현은 지도 밖의 이슈 워크플로에서 진행한다.