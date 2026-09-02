# TMDB API 조사

Type: research
Status: resolved

## Question

TMDB API로 영화·드라마 콘텐츠를 Spring Batch로 수집하려 할 때 알아야 할 사실을 공식 문서에서 확인한다.

- **인증 방식** — API key와 read access token 중 무엇을 쓰는가. 발급 절차와 무료 티어 조건.
- **수집에 쓸 엔드포인트** — 전체 목록을 훑는 방법(`discover`, `popular`, `changes` 등)과 각각의 페이지네이션 한계(최대 페이지 수 제한이 있는가).
- **증분 수집이 가능한가** — 변경분만 가져오는 API가 있는가. 없다면 전량 재수집 외의 방법은 무엇인가.
- **rate limit** — 초당/일일 한도, 동시 요청 제한, 초과 시 응답 형태.
- **한국어 로케일** — `language` 파라미터 동작, 번역이 없을 때의 폴백.
- **이미지 URL 구성** — `configuration` 엔드포인트와 base URL·사이즈 규칙.
- 응답 스키마 중 프론트 계약(`api.json`)의 콘텐츠 스키마에 대응시켜야 할 필드.

출처는 TMDB 공식 문서를 1차로 삼는다.

## Answer

전문은 [`research/tmdb-api.md`](../research/tmdb-api.md) (633줄, 주장마다 출처 URL 명시).

**증분 수집은 가능하다.** 전량 수집은 최초 백필 1회로 끝난다. 축이 둘이고 서로 다른 질문에 답하므로 **둘 다** 써야 한다.

| 축 | 수단 | 답하는 질문 |
| --- | --- | --- |
| 존재 | 일일 ID 익스포트 (`files.tmdb.org/p/exports/…json.gz`) | 어떤 ID가 존재하는가 / 생겼는가 / 사라졌는가 |
| 변경 | `/movie/changes`, `/tv/changes` | 기존 항목 중 무엇이 바뀌었는가 |
| 본문 | 델타 ID에 대해서만 `/movie/{id}`, `/tv/{id}` | 그것은 무엇인가 |

- **일일 익스포트**: 인증 불필요, 매일 07:00 UTC 발행·08:00 UTC 완료(**= 17:00 KST, 배치 스케줄이 여기서 정해진다**), 보관 3개월, JSON Lines라 `FlatFileItemReader`로 스트리밍 가능. 문서에 없는 줄 스키마를 실제로 받아 확인함 — 영화 `{adult, id, original_title, popularity, video}`, TV `{id, original_name, popularity}`. 2026-08-31 영화 파일은 **1,238,234줄**.
- **변경 API**: 기본 24시간, `start_date`/`end_date`로 **최대 14일**(초과 시 error 20), 페이지당 100개. `adult`가 `null`일 수 있어 DTO는 박싱된 `Boolean` 필요.
- **삭제 감지는 익스포트 diff로만 가능** — 변경 API는 삭제를 알려주지 않는다.

**설계를 강제하는 발견: `/discover`로는 전체를 훑을 수 없다.** 페이지 상한 500(error 22). 20건/페이지이므로 도달 가능 상한이 10,000건인데, TMDB 자체 예시 응답이 `total_pages: 38020, total_results: 760385`을 보고한다. 즉 `while (page <= total_pages)` 루프는 501페이지에서 400으로 죽는다. 이 상한은 **오류 코드 표에만 적혀 있고** discover 레퍼런스나 OpenAPI 스키마에는 `maximum` 선언이 없다. `/movie/popular`도 "내부적으로 discover 호출"이라 같은 천장을 공유한다.

**그 외**
- **인증**: `api_key` 쿼리 파라미터와 Bearer(API Read Access Token)의 접근 권한은 문서상 동일. **Bearer 권장** — 배치 요청량에서 자격증명이 액세스 로그에 남지 않는 것이 실질적 차이. 비상업적 이용 무료, 출처 표기와 로고 노출 의무.
- **rate limit**: 약 40 req/s. 문서가 "40 requests per second 범위 어딘가"라는 **근사치**이며 "언제든 바뀔 수 있다"고 명시. 구 10초당 40회 제한은 2019-12-16 해제. 초과 시 429. **일일 한도와 동시성 상한은 문서화되어 있지 않다.**
- **한국어**: `language=ko-KR`. **텍스트 폴백 없음** — TMDB 스태프가 "Language fallbacks are not currently supported"라고 명시(2025-03-14). 번역이 없으면 빈 값으로 오므로 `ko → en → original_*` 체인을 **앱이 직접 만들어야 한다**. 이미지는 반대로 자동 폴백이 문서화되어 있다. 인물명·배역명은 번역되지 않는다.
- **매핑**: 프론트 `ContentDto`는 얇다 — 개봉일·러닝타임·외부 ID를 담을 자리가 없으므로 내부 전용 컬럼이 된다. `ContentDto.id`가 UUID이므로 **`tmdbId` + `mediaType`에 unique 제약이 없으면 배치 재실행 시 행이 중복된다.** `averageRating`/`reviewCount`는 TMDB의 `vote_average`가 아니라 이 서비스 자체 데이터다. TV에는 `runtime`이 아예 없고(`episode_run_time[]` 배열뿐), 상세 응답에 `imdb_id`도 없다. `append_to_response`(최대 20)로 항목당 4회 호출을 1회로 줄일 수 있다.

**메우지 않고 남긴 공백** (전문 9장에 6건). 중요한 둘:
1. 한국어 번역이 없을 때 `title`/`name`이 실제로 무엇을 반환하는지(원제 / 영어 / 빈 문자열) — 문서에 없고 API 키가 없어 확인 불가. 키가 생기면 실행할 `curl` 한 줄을 전문에 적어두었다.
2. 500페이지 상한이 `/movie/changes`에도 적용되는지 — error 22는 전역 코드이고 changes 레퍼런스는 상한을 언급하지 않는다. 100건/페이지면 50,000건 천장이고 14일 창이면 도달 가능. **1일 창으로 고정하면 이 질문을 우회한다**(권장).
