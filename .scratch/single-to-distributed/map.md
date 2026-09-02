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

## Not yet specified

- **캐시 전략** — 무엇을 캐시하고 무효화를 어떻게 할 것인가. 추상화 뒤에 둔다는 것까지는 정해졌고(이음매 8번), **캐시 대상**은 도메인 모델이 정해져야 물을 수 있다. Redis가 1일차에 있으므로 단일 단계에서도 로컬 캐시 대신 Redis를 바로 쓸지가 함께 걸린다.
- **AWS 배포 형상** — ECS 태스크 구성, Nginx 리버스 프록시 배치, 이미지 빌드·배포 경로. **"내렸다 올려도 보존되어야 하는 상태"의 윤곽은 이음매 결정으로 좁혀졌다** — 시청 세션은 Redis에 있어 휘발되고(허용 가능한가?), 알림·DM은 DB라 남는다. 확정은 배포 형상이 잡혀야 가능하다.
- **예외·에러 응답 계약** — `api.json`이 선언한 상태코드와 커스텀 예외 구조를 어떻게 대응시킬지.
- **로깅과 Actuator 커스텀 메트릭** — 특히 배치 모니터링. 배치 설계가 끝나야 무엇을 재야 할지 정해진다.
- **세부 정책들** — 어드민 초기화, 임시 비밀번호 3분 만료, 계정 잠금과 강제 로그아웃의 상호작용.
- **분산 전환의 절차** — 무엇이 바뀌는지는 이음매 결정으로 확정됐다(브로커 릴레이, 파일 저장소, 이벤트 발행, 배치 잠금). 남은 것은 **어떤 순서로 밟고 무엇으로 완료를 판정할 것인가**이며, [12주 마일스톤 구획]과 [이음매를 감싸는 테스트 전략]이 정해져야 선명해진다.

## Out of scope

- **Elasticsearch 검색 리팩토링** — 검색 품질 개선은 "분산 전환을 겪어본다"는 이번 목적지와 축이 다르다. `build.gradle`에 의존성은 남겨두되 이번 지도에서는 다루지 않는다.
- **AWS 상시 운용·비용 최적화** — 검증 시에만 올렸다 내리므로, 상시 운영을 전제로 한 결정(오토스케일링 정책, 비용 튜닝)은 목적지 밖이다.
- **구현 자체** — 이 지도는 결정만 산출한다. 12주의 구현은 지도 밖의 이슈 워크플로에서 진행한다.