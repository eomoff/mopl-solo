# TMDB API 조사

조사 시점: 2026-09-02
1차 출처: TMDB 공식 개발자 문서(<https://developer.themoviedb.org>). 문서 페이지는 URL 끝에 `.md`를 붙이면 원문 마크다운을 그대로 받을 수 있어, 아래 인용은 대부분 그 원문에서 가져왔다.
일부 항목은 실제 HTTP 호출로 직접 검증했고, 검증한 것과 문서만 본 것을 구분해 표기했다.

- ✅ **실측 확인** — 이 조사 중 실제로 요청해서 응답을 본 것
- 📄 **문서 확인** — 공식 문서에 명시된 것
- ⚠️ **미확인** — 문서에 없고 실측도 못 한 것. 추측하지 않고 남겨둔다.

---

## 1. 인증 방식

출처: <https://developer.themoviedb.org/docs/authentication-application>, <https://developer.themoviedb.org/docs/getting-started>

📄 v3 API는 **두 가지 인증 방식을 모두 받아들이며, 권한 수준이 동일하다.**

> "Version 3 is controlled by either a single query parameter, `api_key`, or by using your access token as a `Bearer` token."
> "Both authentication methods provide the same level of access, and which one you choose is completely up to you."

| 방식 | 전달 위치 | 발급 위치 |
| :--- | :--- | :--- |
| API Key (v3) | 쿼리 파라미터 `?api_key=...` | 계정 설정 → API |
| API Read Access Token | 헤더 `Authorization: Bearer <token>` | 계정 설정 → API 섹션의 *API Read Access Token* |

```
curl --request GET \
     --url 'https://api.themoviedb.org/3/movie/11' \
     --header 'Authorization: Bearer <<access_token>>'
```

### 결론: Bearer 토큰을 쓴다

- 문서가 "The default method to authenticate is with your access token"이라고 **Bearer를 기본으로 명시**한다.
- v3·v4 양쪽에서 같은 토큰을 쓸 수 있다("a single authentication process that you can use across both the v3 and v4 methods").
- 실무적으로 더 중요한 이유: 키가 쿼리스트링에 실리지 않으므로 **액세스 로그·APM·에러 리포트에 자격증명이 남지 않는다.** 배치는 요청 수가 많아 로그에 남는 양도 많다.
- Spring 쪽에서는 `RestClient`/`WebClient`의 `defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)` 한 줄로 끝나서, 매 요청 URI에 쿼리 파라미터를 붙이는 것보다 배선이 깔끔하다.

> 참고: 문서가 "v4 토큰"이라 부르지 않고 **"API Read Access Token"**이라 부른다. 계정 페이지에서도 이 이름으로 노출된다. 읽기 전용이므로 수집 배치 용도에 정확히 맞다.

### 발급 절차와 무료 티어 조건

📄 발급: 계정 설정 페이지의 API 링크(<https://www.themoviedb.org/settings/api>)에서 신청. 이용약관 동의가 선행 조건이다.

> "Before being issued an API key you will have to agree to our terms of use."
> "Please note that the API registration process *is not* optimized for mobile devices so you should access these pages on a desktop computer and browser."

📄 무료 조건 (출처: <https://developer.themoviedb.org/docs/faq>):

> "Our API is free to use for non-commercial purposes as long as you attribute TMDB as the source of the data and/or images."
> "Your project is considered commercial if the primary purpose is to create revenue for the benefit of the owner."

개인 연습 프로젝트는 비상업이므로 무료 티어에 해당한다. 다만 **귀속 표기 의무**가 붙는다:

> "You shall use the TMDB logo to identify your use of the TMDB APIs. You shall place the following notice prominently on your application: **"This product uses the TMDB API but is not endorsed or certified by TMDB."**"
> "When attributing TMDB, the attribution must be within your application's "About" or "Credits" type section."

승인된 로고는 <https://www.themoviedb.org/about/logos-attribution>.

📄 SLA는 없다: "We do not currently provide an SLA." → 배치가 TMDB 장애에 물려 통째로 실패하지 않도록 재시도·부분 커밋 설계가 필요하다는 뜻.

---

## 2. 카탈로그를 훑는 엔드포인트와 페이지네이션 한계

### 2.1 페이지 상한은 500 — 이것이 설계를 가른다

📄 페이지 상한은 discover/popular 각 레퍼런스 페이지에는 **적혀 있지 않고**, 에러 코드 표에만 있다.

출처: <https://developer.themoviedb.org/docs/errors>

| Code | HTTP Status | Message |
| :--- | :--- | :--- |
| 20 | 422 | Invalid date range: Should be a range no longer than 14 days. |
| 22 | 400 | **Invalid page: Pages start at 1 and max at 500.** They are expected to be an integer. |
| 25 | 429 | Your request count (#) is over the allowed limit of (40). |
| 27 | 400 | Too many append to response objects: The maximum number of remote calls is 20. |

📄 `/discover/movie`의 OpenAPI 정의에서 `page`는 `{"type":"integer","format":"int32","default":1}`일 뿐 **`maximum` 제약이 선언되어 있지 않다.** 즉 스펙만 보고 클라이언트를 생성하면 500 상한을 놓친다. 배치 코드에 상한을 직접 박아야 한다.

출처: <https://developer.themoviedb.org/reference/discover-movie>

### 2.2 그래서 discover로는 전체를 훑을 수 없다

📄 `/discover/movie` 레퍼런스의 **공식 예제 응답**이 이 문제를 그대로 보여준다:

```json
{ "page": 1, "results": [ ...20건... ], "total_pages": 38020, "total_results": 760385 }
```

- 한 페이지 **20건**, `total_pages`는 **38,020**이라고 응답한다.
- 그런데 `page`는 500까지만 유효하다 (에러 22).
- 따라서 한 쿼리로 도달 가능한 최대 건수는 **500 × 20 = 10,000건**. 전체 760,385건 중 **1.3%**다.

`/discover/tv` 예제도 같은 구조다: `total_pages: 7414`, `total_results: 148265` → 역시 10,000건에서 잘린다.
출처: <https://developer.themoviedb.org/reference/discover-tv>

> ⚠️ 주의: 응답의 `total_pages`는 **믿으면 안 된다.** 38,020을 반환하지만 501페이지를 요청하면 400(코드 22)이다. `while (page <= total_pages)` 루프를 그대로 쓰면 501페이지에서 배치가 죽는다. 종료 조건은 `min(total_pages, 500)`이어야 한다.

### 2.3 `/movie/popular`은 discover의 별칭이다

📄 출처: <https://developer.themoviedb.org/reference/movie-popular-list>

> "This call is really just a discover call behind the scenes."

문서가 등가 쿼리를 직접 제시한다:

```
/3/discover/movie?include_adult=false&include_video=false&language=en-US&page=1&sort_by=popularity.desc
```

즉 `popular`, `top_rated`, `now_playing` 계열은 **discover의 프리셋일 뿐 별도의 수집 경로가 아니다.** 같은 500페이지 상한을 공유하며, 인기순 상위 10,000건만 준다.

### 2.4 엔드포인트별 페이지네이션 정리

| 엔드포인트 | 페이지당 건수 | 페이지 상한 | 근거 |
| :--- | :--- | :--- | :--- |
| `/3/discover/movie` | 20 | 500 (=10,000건) | 예제 응답 + 에러 22 |
| `/3/discover/tv` | 20 | 500 (=10,000건) | 예제 응답 + 에러 22 |
| `/3/movie/popular` 등 | 20 | 500 | discover 별칭 |
| `/3/movie/changes` | **100** | 문서상 명시 없음 ⚠️ | 아래 3장 |
| `/3/tv/changes` | **100** | 문서상 명시 없음 ⚠️ | 아래 3장 |
| 일별 ID 익스포트 | 파일 전체 | 페이지네이션 없음 | 아래 3장 |

### 2.5 discover를 굳이 쓰려면 — 슬라이싱

10,000건 상한은 **쿼리 단위**다. 따라서 필터로 결과 집합을 10,000건 미만으로 쪼개면 여러 쿼리로 전체를 덮을 수 있다. `/discover/movie`는 38개 파라미터를 제공한다.

📄 슬라이싱에 쓸 만한 필터 (출처: <https://developer.themoviedb.org/reference/discover-movie>):

- `primary_release_date.gte` / `primary_release_date.lte` (`format: date`) — 연도·월 단위로 자르기
- `primary_release_year` (int32)
- `with_original_language` — 예: `ko`
- `region` + `with_release_type` — 국가별 개봉 유형 필터
- `sort_by` 기본값 `popularity.desc`, 14종 지원
- `include_adult` 기본 `false`
- `vote_count.gte` — 데이터가 빈약한 항목 걸러내기

TV는 `first_air_date.gte/lte`, `with_origin_country` 등 대응 필터를 쓴다.
출처: <https://developer.themoviedb.org/reference/discover-tv>

📄 `region` 파라미터의 의미 (출처: <https://developer.themoviedb.org/docs/region-support>):

> "In the event that we don't have a release date entered for the country you are searching for, we simply default back to the primary release date like always."

릴리스 타입 코드: 1 Premiere / 2 Theatrical(limited) / 3 Theatrical / 4 Digital / 5 Physical / 6 TV.

**다만 이 슬라이싱은 "훑기"의 대안이지 정답이 아니다.** 76만 건을 연 단위로 자르면 100회 이상의 쿼리 트리(각각 최대 500페이지)를 관리해야 하고, 슬라이스 하나가 10,000건을 넘으면 조용히 누락된다. 다음 장의 일별 익스포트가 이 문제를 통째로 없앤다.

---

## 3. 증분 수집 — 핵심 결론

**결론: 전량 재수집은 필요 없다. 두 개의 1차 수단이 있고, 둘을 조합하는 것이 정답이다.**

### 3.1 일별 ID 익스포트 (전체 목록의 소스 오브 트루스)

📄 출처: <https://developer.themoviedb.org/docs/daily-id-exports>

> "We currently publish a set of daily ID file exports. These are not, nor intended to be full data exports. Instead, they contain a list of the valid IDs you can find on TMDB and some higher level attributes that are helpful for filtering items like the adult, video and popularity values."

**인증 불필요:**

> "There is currently no authentication on these files since they are not very useful unless you're a user of our service. Please note that this could change at some point in the future so if you start having problems accessing these files, check this document for updates."

**생성 시각과 보존 기간:**

> "The export job runs every day starting at around 7:00 AM UTC, and all files are available by 8:00 AM UTC."
> "These files are only made available for 3 months after which they are automatically deleted."

UTC 08:00 = **KST 17:00**. 배치 스케줄은 KST 17:00 이후로 잡아야 한다. 보존이 3개월이므로 **3개월 이상 배치가 멈춰 있었다면 과거 파일로 복구할 수 없다.**

**파일 목록** (경로는 모두 `/p/exports`, 날짜 형식 `MM_DD_YYYY`):

| 미디어 타입 | 파일명 |
| :--- | :--- |
| Movies | `movie_ids_MM_DD_YYYY.json.gz` |
| TV Series | `tv_series_ids_MM_DD_YYYY.json.gz` |
| People | `person_ids_MM_DD_YYYY.json.gz` |
| Collections | `collection_ids_MM_DD_YYYY.json.gz` |
| TV Networks | `tv_network_ids_MM_DD_YYYY.json.gz` |
| Keywords | `keyword_ids_MM_DD_YYYY.json.gz` |
| Production Companies | `production_company_ids_MM_DD_YYYY.json.gz` |
| Adult Movies | `adult_movie_ids_MM_DD_YYYY.json.gz` |
| Adult TV Series | `adult_tv_series_ids_MM_DD_YYYY.json.gz` |
| Adult People | `adult_person_ids_MM_DD_YYYY.json.gz` |

**파일 구조 — Spring Batch와 궁합이 좋다:**

> "These files themselves are not a valid JSON object. Instead, each line is. Most systems, tools and languages have easy ways of scanning lines in files (skipping and buffering) without having to load the entire file into memory. The assumption here is that you can read every line easily, and you can expect each line to contain a valid JSON object."

JSON Lines이므로 `FlatFileItemReader` + 줄 단위 Jackson 파싱으로 스트리밍 처리된다. 전체를 메모리에 올릴 필요가 없다.

#### ✅ 실측 확인 (2026-09-02, `08_31_2026` 파일 기준)

문서에 각 줄의 필드 스키마가 **명시되어 있지 않아** 실제로 내려받아 확인했다.

```
GET https://files.tmdb.org/p/exports/movie_ids_08_31_2026.json.gz  → 200, 27,700,691 bytes (gzip)
줄 수: 1,238,234
```

| 파일 | 실제 첫 줄 |
| :--- | :--- |
| `movie_ids` | `{"adult":false,"id":3924,"original_title":"Blondie","popularity":1.8133,"video":false}` |
| `tv_series_ids` | `{"id":1,"original_name":"プライド","popularity":3.8242}` |
| `person_ids` | `{"adult":false,"id":16767,"name":"Aki Kaurismäki","popularity":2.4825}` |
| `collection_ids` | `{"id":10,"name":"Star Wars Collection"}` |
| `tv_network_ids` | `{"id":1,"name":"Fuji TV"}` |
| `keyword_ids` | `{"id":378,"name":"prison"}` |
| `production_company_ids` | `{"id":1,"name":"Lucasfilm Ltd."}` |
| `adult_movie_ids` | `{"adult":true,"id":26228,"original_title":"Rocco - Perfect Girls 5","popularity":0.0,"video":false}` |

읽어낼 점:
- 영화 파일에만 `adult`, `video`가 있고 **TV 파일에는 없다.** TV는 `id` / `original_name` / `popularity` 3개뿐이다. 공통 리더를 쓰려면 스키마 차이를 흡수해야 한다.
- `popularity`가 들어 있어 **수집 우선순위를 파일 단계에서 정할 수 있다.** 상세 조회는 요청 1건씩 들기 때문에, 124만 건 전체를 매번 돌리는 대신 popularity 상위 N건만 먼저 채우는 전략이 가능하다.
- 실측 124만 줄은 discover 예제의 `total_results: 760385`(문서 예제 시점 값)보다 크다. **discover로는 애초에 닿을 수 없는 규모**임을 확인해 준다.

**한계:** 이 파일은 ID 목록일 뿐이다. 제목·줄거리·포스터·장르는 없으므로 **ID마다 상세 엔드포인트를 따로 호출해야 한다.** 즉 익스포트는 "무엇이 존재하는가"를 알려주고, "그것이 무엇인가"는 API가 알려준다.

### 3.2 변경 목록 API (증분의 소스 오브 트루스)

📄 출처: <https://developer.themoviedb.org/docs/tracking-content-changes>

> "There are two aspects to this: first, tracking which ID's were changed and then second, calling those individual changes."

**1단계 — 변경된 ID 목록**

| 엔드포인트 | 문서 |
| :--- | :--- |
| `GET /3/movie/changes` | <https://developer.themoviedb.org/reference/changes-movie-list> |
| `GET /3/tv/changes` | <https://developer.themoviedb.org/reference/changes-tv-list> |
| `GET /3/person/changes` | <https://developer.themoviedb.org/reference/changes-people-list> |

> "Get a list of all of the movie ids that have been changed in the past 24 hours."
> "You can query this method up to 14 days at a time. Use the `start_date` and `end_date` query parameters. **100 items are returned per page.**"

📄 파라미터: `start_date` (`format: date`), `end_date` (`format: date`), `page` (기본 1). 날짜 형식은 `YYYY-MM-DD`(에러 23), 범위 14일 초과 시 **422 / 코드 20**("Invalid date range: Should be a range no longer than 14 days").

📄 응답 형태 (레퍼런스 예제):

```json
{
  "results": [ {"id": 1120293, "adult": false}, {"id": 1120298, "adult": null}, ... ],
  "page": 3, "total_pages": 57, "total_results": 5700
}
```

- `adult`가 **`null`일 수 있다.** DTO에서 `Boolean` 박싱 타입으로 받아야 한다. `boolean`이면 역직렬화가 깨진다.
- 변경 목록도 **ID만** 준다. 상세는 별도 호출이다.

**2단계 — 개별 항목의 필드 단위 변경 내역**

📄 `GET /3/movie/{movie_id}/changes` (<https://developer.themoviedb.org/reference/movie-changes>)
파라미터: `movie_id`, `start_date`, `end_date`, `page`.

> "Get the changes for a movie. By default only the last 24 hours are returned."
> "This call will then return the actual field level data that was changed, just like our public change logs you can find on the website. You can either consume these changes in their entirety, or selective grab the data that is important to you."

응답은 `changes[].key` / `items[].action` / `items[].time` 구조다:

```json
{"changes":[{"key":"images","items":[{"id":"...","action":"added","time":"2023-04-08 16:35:05 UTC","iso_639_1":"","iso_3166_1":"","value":{"poster":{"file_path":"/s9ZrHprviFCx3azfWNBtt1LPSnL.jpg"}}}]}]}
```

`key`로 올 수 있는 값의 전체 목록은 `/configuration`의 `change_keys` 배열(53개)에 들어 있다: `adult`, `air_date`, `genres`, `images`, `imdb_id`, `name`, `overview`, `poster_path`, `release_date`, `runtime`, `title`, `translations`, `videos` 등.
출처: <https://developer.themoviedb.org/reference/configuration-details>

### 3.3 권장 설계 — 두 축을 나눈다

| 축 | 수단 | 주기 | 답하는 질문 |
| :--- | :--- | :--- | :--- |
| **존재 동기화** | 일별 ID 익스포트 | 일 1회 (KST 17:00 이후) | 새로 생긴 ID / 사라진 ID는? |
| **내용 동기화** | `/movie/changes`, `/tv/changes` | 일 1회 (24시간 창) | 기존 항목 중 뭐가 바뀌었나? |
| **본문 적재** | `/movie/{id}`, `/tv/{id}` | 위 두 축이 뱉은 ID에 대해서만 | 그 항목의 실제 내용은? |

즉 **전량 재수집은 최초 1회(백필)만** 하고, 이후는 위 두 축의 델타만 상세 조회한다. 최초 백필도 익스포트 파일의 `popularity` 순으로 우선순위를 매겨 나눠 돌릴 수 있다.

#### 이 설계에서 주의할 점

1. ⚠️ **변경 목록에 500페이지 상한이 걸리는지 문서에 없다.** 에러 22("Pages start at 1 and max at 500")는 전역 에러 코드로만 존재하고, `/movie/changes` 레퍼런스에는 상한이 명시되지 않았다. 100건/페이지 × 500페이지 = 50,000건이 이론적 천장인데, 레퍼런스 예제는 하루치가 `total_results: 5700`이다. **14일 창을 쓰면 이 천장에 닿을 수 있다.** → **1일 창으로 매일 돌리는 것이 안전하다.** 14일 창은 "배치가 며칠 밀렸을 때 따라잡는 용도"로만 쓰고, 그때도 하루씩 쪼개서 호출하는 편이 낫다. (실측 미검증 — API 키 확보 후 확인할 것)

2. **익스포트의 UTC 08:00과 변경 API의 "지난 24시간"은 기준점이 다르다.** 두 축을 같은 잡에서 돌리더라도 워터마크는 따로 관리해야 한다. 변경 API 쪽은 `start_date`/`end_date`를 명시적으로 넘겨 "마지막 성공 시각"을 저장하는 편이 재실행에 안전하다.

3. **삭제 감지는 익스포트로만 가능하다.** 변경 API는 삭제된 항목을 알려주지 않는다(문서에 명시 없음, ⚠️ 미확인). 어제 익스포트에는 있었는데 오늘 없는 ID = 삭제 후보로 다뤄야 한다.

4. **3개월 보존**이라 익스포트 기반 복구 창은 3개월이다. 그 이상 방치되면 전량 재수집이 유일한 복구 수단이다.

---

## 4. Rate limit

📄 출처: <https://developer.themoviedb.org/docs/rate-limiting>

> **Legacy Rate Limits**
> "As of December 16, 2019, we have disabled the original API rate limiting (40 requests every 10 seconds.)"

> "While our legacy rate limits have been disabled for some time, we do still have some upper limits to help mitigate needlessly high bulk scraping. **They sit somewhere in the 40 requests per second range.** This limit could change at any time so be respectful of the service we have built and respect the `429` if you receive one."

정리:

| 항목 | 값 | 상태 |
| :--- | :--- | :--- |
| 초당 한도 | **약 40 req/s** ("somewhere in the 40 requests per second range") | 📄 문서. **정확한 수치가 아니라 근사치로 서술됨** |
| 구 한도 (40 req / 10s) | 2019-12-16부로 **비활성화** | 📄 문서 |
| 일일 한도 | 문서에 **언급 없음** | ⚠️ 미확인 |
| 동시 요청 수 제한 | 문서에 **언급 없음** | ⚠️ 미확인 |
| IP 기준 / 키 기준 | 문서에 **언급 없음** | ⚠️ 미확인 |
| 초과 시 | HTTP **429** | 📄 문서 |

📄 에러 코드 25 = HTTP 429, 메시지 `"Your request count (#) is over the allowed limit of (40)."`
출처: <https://developer.themoviedb.org/docs/errors>

### ✅ 실측 확인 — 에러 응답 바디 형태

문서에 에러 JSON 스키마가 없어 직접 호출해 확인했다.

```
$ curl -i 'https://api.themoviedb.org/3/movie/550'
HTTP/2 401
content-type: application/json; charset=utf-8

{"status_code":7,"status_message":"Invalid API key: You must be granted a valid key.","success":false}
```

즉 에러 바디는 **`{success, status_code, status_message}`** 3필드 구조다. 429도 같은 형태로 `status_code: 25`가 온다고 보는 것이 자연스럽다(⚠️ 429 자체는 실측하지 못함 — 일부러 유발하지 않았다).

### ⚠️ 확인하지 못한 것: 429 응답 헤더

`Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` 등의 헤더가 오는지 **문서에 서술이 없고, 실측도 못 했다.** 401 응답 헤더에는 rate limit 관련 헤더가 전혀 없었다.
과거 40/10s 시절에는 `X-RateLimit-*` 헤더가 있었다고 알려져 있으나, 그 한도가 폐지된 지금도 유지되는지는 **확인 불가**다. → **`Retry-After`가 있다고 가정한 백오프 구현은 위험하다.** 헤더가 없을 때도 동작하는 지수 백오프를 기본으로 하고, 헤더가 있으면 그것을 우선하는 형태로 만들어야 한다.

### 배치 설계 함의

- 40 req/s가 "근사치이며 언제든 바뀔 수 있다"고 명시되어 있으므로, **한도에 붙여서 돌리면 안 된다.** 20~25 req/s 정도로 여유를 두는 것이 문서의 "be respectful" 요청에 부합한다.
- 124만 건을 20 req/s로 상세 조회하면 약 **17시간**이다. 최초 백필을 하루 안에 끝내려는 설계는 비현실적이며, 여러 날에 걸쳐 나눠 돌리거나 popularity 상위만 우선 적재하는 편이 맞다.
- Spring Batch 청크 단위 병렬(`TaskExecutor`)을 쓸 때 **동시 요청 수 제한이 문서화되어 있지 않다**는 점을 감안해, 스로틀은 스레드 수가 아니라 **요청 레이트**로 걸어야 한다.
- 429는 재시도 대상, 4xx(400/401/404)는 재시도 무의미 — `RetryPolicy`에서 구분해야 한다. 특히 코드 34(404, "The resource you requested could not be found")는 삭제된 ID라 **스킵 처리**가 맞다.
- `append_to_response`로 서브 요청을 묶으면 요청 수를 줄일 수 있다(→ 7장). **최대 20개**(에러 27).

---

## 5. 한국어 로케일

### 5.1 `language` 파라미터

📄 출처: <https://developer.themoviedb.org/docs/languages>

> "The language code system we use is ISO 639-1."
> "You'll usually find our language codes mated to a country code in the format of `en-US`. The country codes in use here are ISO 3166-1."

→ 한국어는 **`language=ko-KR`**. `/discover`, `/movie/{id}`, `/tv/{id}`, `/genre/*/list` 등이 모두 지원하며, `/discover/movie`의 `language` 기본값은 `en-US`다(OpenAPI 정의).

📄 번역이 지원되지 **않는** 영역이 명시되어 있다:

> "While most of our metadata endpoints support translated data, there are still a few gaps that do not. **The two main areas that are not are person names and characters.** We're working to support this."

→ 인물 이름과 배역명은 `ko-KR`을 줘도 번역되지 않는다.

### 5.2 번역이 없을 때의 폴백 — **텍스트는 폴백이 없다**

**이것이 한국어 수집의 가장 중요한 제약이다.**

TMDB 공식 문서(`docs/languages`)에는 텍스트 폴백 동작에 관한 서술이 **아예 없다.** 대신 TMDB 운영진이 공개 포럼에서 반복적으로 답한 내용이 있다:

- **Travis Bell (TMDB 창업자/Staff), 2025-03-14:** "Language fallbacks are not currently supported."
  출처: <https://www.themoviedb.org/talk/67c7890473b7f95da9c2856f>
- **ticao2 (TMDB Moderator), 2023-10-14:** 원본 줄거리를 함께 받을 옵션은 없으며("There is no such data option. original_overview"), 폴백 기능은 로드맵의 To-Do에 있다.
  출처: <https://www.themoviedb.org/talk/65280f6afd63005d7c4a30ba>

→ **`language=ko-KR`로 요청했는데 한국어 번역이 없으면 해당 텍스트 필드는 빈 값으로 온다.** 자동으로 영어가 채워지지 않는다.

⚠️ **미확인:** `overview`가 빈 문자열로 오는 것과 달리 `title`/`name`이 어떻게 채워지는지는 **1차 문서로 확인하지 못했다.** (원제로 떨어지는지, 영어 제목으로 떨어지는지, 빈 문자열인지) API 키가 없어 실측도 하지 못했다.
→ **키를 발급받은 직후 다음 한 줄로 반드시 확인할 것.** 이 결과에 따라 아래 대응 전략의 필요 범위가 달라진다.

```bash
curl -s -H "Authorization: Bearer $TMDB_TOKEN" \
  'https://api.themoviedb.org/3/movie/550?language=ko-KR' | jq '{title, original_title, overview, tagline}'
# 한국어 번역이 없는 마이너 작품 ID로도 한 번 더 확인
```

### 5.3 대응 전략

폴백이 없으므로 애플리케이션이 직접 만들어야 한다. 선택지:

| 전략 | 요청 수 | 비고 |
| :--- | :--- | :--- |
| **A. 2회 호출** — `ko-KR`로 받고 빈 필드가 있으면 `en-US`로 재호출해 메움 | 최악 2배 | 단순하고 명시적. 빈 필드가 있을 때만 두 번째 호출 → 실제 증가폭은 훨씬 작다 |
| **B. `append_to_response=translations`** — 1회 호출로 전체 번역 배열을 받아 앱에서 고름 | **1회** | 요청 수 면에서 유리. 다만 응답 크기가 커진다 |
| **C. `original_title`/`original_name`로 대체** | 1회 | 이미 응답에 포함된 필드라 추가 비용 0. 단 한국 콘텐츠가 아니면 외국어 원제가 노출된다 |

→ **B를 기본으로 하고, `translations`에도 `ko`가 없으면 `en` → `original_*` 순으로 떨어뜨리는 폴백 체인**이 요청 수와 품질 모두에서 합리적이다. 이 폴백 규칙은 도메인 계층에 명시적으로 두어야 나중에 TMDB가 폴백을 지원하기 시작해도 갈아끼우기 쉽다.

### 5.4 장르 이름은 로케일별로 제공된다

📄 `GET /3/genre/movie/list?language=ko-KR` — 파라미터는 `language` 하나.
레퍼런스 예제가 독일어 응답을 보여준다: `{"genres":[{"id":28,"name":"Action"},{"id":12,"name":"Abenteuer"},...]}`
출처: <https://developer.themoviedb.org/reference/genre-movie-list>

→ **중요:** discover/popular의 **목록 응답은 장르를 `genre_ids`(정수 배열)로만 준다.** 상세 응답(`/movie/{id}`)은 `genres: [{id, name}]`로 준다. 목록만 쓰고 장르명을 채우려면 `/genre/movie/list?language=ko-KR`와 `/genre/tv/list?language=ko-KR`를 미리 받아 **캐시해 두고 조인**해야 한다. 이 두 호출은 하루 1회면 충분하다.

### 5.5 이미지의 로케일 폴백은 **문서화되어 있다** (텍스트와 다름)

📄 출처: <https://developer.themoviedb.org/docs/image-languages>

> **`poster_path`**: "The `poster_path` will query the language you specify in your query first and default back to the highest rated image of the media's "original language" if it's present. If that image doesn't exist, it simply falls back to the highest rated. **It's important to note that even though our language query parameter supports regional lookups, these regional variants are not supported for images at this time.**"

> **`backdrop_path`**: "Since 99% of backdrops don't contain a language, the default lookup for a backdrop is to simply query for the highest rated backdrop with no language. If it doesn't exist, then we return the overall highest rated."

→ **포스터는 텍스트와 달리 자동 폴백이 있다.** `ko-KR`로 요청해도 `poster_path`는 (한국어 포스터 → 원어 포스터 → 최고 평점 포스터) 순으로 항상 채워질 가능성이 높다. 다만 **이미지는 지역 변형(`ko-KR`)을 지원하지 않고 언어(`ko`)만 본다.**

📄 `/images` 계열을 부를 때는 `language`가 **필터로 작동**하므로 `include_image_language`로 폴백을 열어줘야 한다:

> "Remember, when you query one of the `/images` methods, your `language` param will filter images. Since you'll usually want to query additional languages, you'll want to use the `include_image_language` query parameter. **Think of this as a means to provide a fallback.**"

```
/3/movie/550?append_to_response=images&language=ko-KR&include_image_language=ko,en,null
```

`null`은 "언어 태그가 없는 이미지"를 뜻한다.

---

## 6. 이미지 URL 구성

📄 출처: <https://developer.themoviedb.org/docs/image-basics>, <https://developer.themoviedb.org/reference/configuration-details>

> "In order to generate a fully working image URL, you'll need 3 pieces of data. Those pieces are a `base_url`, a `file_size` and a `file_path`."

```
{secure_base_url} + {size} + {file_path}
https://image.tmdb.org/t/p/  +  w500  +  /1E5baAaEse26fej7uHcjOgEE2t2.jpg
→ https://image.tmdb.org/t/p/w500/1E5baAaEse26fej7uHcjOgEE2t2.jpg
```

`file_path`는 **앞에 `/`가 붙은 채로** 응답에 들어 있다. 문자열 결합 시 `/`를 중복해서 넣지 않도록 주의.

📄 `GET /3/configuration` 응답의 `images` 블록 (레퍼런스 예제 원문):

```json
{
  "base_url": "http://image.tmdb.org/t/p/",
  "secure_base_url": "https://image.tmdb.org/t/p/",
  "backdrop_sizes": ["w300", "w780", "w1280", "original"],
  "logo_sizes": ["w45", "w92", "w154", "w185", "w300", "w500", "original"],
  "poster_sizes": ["w92", "w154", "w185", "w342", "w500", "w780", "original"],
  "profile_sizes": ["w45", "w185", "h632", "original"],
  "still_sizes": ["w92", "w185", "w300", "original"]
}
```

📄 FAQ: "What about SSL? It's currently available API wide. This includes both the API endpoints and assets served via our CDN. **We strongly recommend you use SSL.**" → `base_url`(http)이 아니라 **`secure_base_url`(https)을 쓴다.**

📄 로고는 SVG/PNG 두 형식:

> "Company and network logos are available in two formats, SVG and PNG. All of the `logo_path` fields will return a .png."
> "For SVG's, you should call the original image size since we don't resize them."

### 설계 함의

- **`/configuration`을 매 요청 부르지 않는다.** 정적에 가까운 데이터이므로 배치 시작 시 1회 조회 후 캐시(또는 Redis)에 올려두고 쓴다. 문서도 "static lists of data we use throughout the database"라고 설명한다(<https://developer.themoviedb.org/docs/getting-started>).
- **DB에는 완성 URL이 아니라 `file_path`를 저장하는 편이 낫다.** base_url이나 선호 사이즈가 바뀌면 전체 행을 갱신해야 하기 때문이다. FE 계약의 `thumbnailUrl`은 조회 시점에 조립한다.
- ⚠️ 사이즈 목록이 **응답 시점에 바뀔 수 있는 값**이므로 `w500` 같은 문자열을 상수로 박아두면 언젠가 깨진다. 최소한 "설정에서 읽되 없으면 `original`" 정도의 방어는 필요하다.

---

## 7. 프론트 계약(`api.json`)에 대응시킬 응답 필드

### 7.1 FE 계약의 콘텐츠 스키마

`project-mopl-fe-1.0.5.zip` 내 `api.json`의 `ContentDto`:

| 필드 | 타입 | required |
| :--- | :--- | :--- |
| `id` | string(uuid) | ✅ |
| `type` | enum `movie` \| `tvSeries` \| `sport` | ✅ |
| `title` | string | ✅ |
| `description` | string | ✅ |
| `thumbnailUrl` | string | ✅ |
| `tags` | string[] | ✅ |
| `averageRating` | double | ✅ |
| `reviewCount` | int32 | ✅ |
| `watcherCount` | int64 | ✅ |

**먼저 짚어야 할 사실 세 가지:**

1. **FE 계약의 콘텐츠 스키마는 매우 얇다.** 개봉일·러닝타임·외부 ID를 담을 자리가 **없다.** 즉 이 필드들은 FE에 노출되지 않는 **내부 전용 컬럼**이다. 수집은 하되 DTO에는 나가지 않는다.
2. **`id`가 UUID**다. TMDB의 정수 ID를 그대로 쓸 수 없다. → **`tmdbId` (+ `mediaType`) 를 별도 컬럼으로 두고 유니크 제약을 걸어야** 배치 재실행 시 멱등성이 확보된다. 이것이 없으면 배치를 두 번 돌릴 때 중복 행이 생긴다.
3. **`averageRating` / `reviewCount` / `watcherCount`는 TMDB에서 오지 않는다.** 이 서비스 자체의 리뷰·시청 데이터다. TMDB의 `vote_average`/`vote_count`와 **혼동하면 안 된다.** 수집 시점에는 0으로 초기화된다.
4. **`type`에 `sport`가 있지만 TMDB는 스포츠를 제공하지 않는다.** TMDB 수집 배치는 `movie`와 `tvSeries`만 채운다. `sport`는 별도 소스가 필요하다(이번 조사 범위 밖).

### 7.2 매핑표

| FE 필드 | 영화 (`GET /3/movie/{id}?language=ko-KR`) | TV (`GET /3/tv/{id}?language=ko-KR`) | 비고 |
| :--- | :--- | :--- | :--- |
| `type` | `"movie"` 고정 | `"tvSeries"` 고정 | |
| `title` | `title` (폴백: `original_title`) | `name` (폴백: `original_name`) | 5.2의 폴백 체인 적용 |
| `description` | `overview` | `overview` | **한국어 없으면 빈 값** → 폴백 필수 |
| `thumbnailUrl` | `secure_base_url` + size + `poster_path` | 동일 | `poster_path`가 `null`일 수 있음 → 기본 이미지 처리 필요 |
| `tags` | `genres[].name` | `genres[].name` | 목록 응답에서는 `genre_ids` → 장르 사전 조인 (5.4) |
| `averageRating` | — | — | 자체 산출. TMDB `vote_average` 아님 |
| `reviewCount` | — | — | 자체 산출 |
| `watcherCount` | — | — | 자체 산출 |

### 7.3 내부 컬럼으로 수집할 필드 (FE 계약 밖)

📄 `/3/movie/{movie_id}` 응답 스키마 (출처: <https://developer.themoviedb.org/reference/movie-details>)

`adult`, `backdrop_path`, `belongs_to_collection{id,name,poster_path,backdrop_path}`, `budget`, `genres[{id,name}]`, `homepage`, `id`, **`imdb_id`**, `origin_country[]`, `original_language`, `original_title`, `overview`, `popularity`, `poster_path`, `production_companies[]`, `production_countries[]`, **`release_date`**, `revenue`, **`runtime`**, `spoken_languages[]`, `status`, `tagline`, `title`, `video`, `vote_average`, `vote_count`

📄 `/3/tv/{series_id}` 응답 스키마 (출처: <https://developer.themoviedb.org/reference/tv-series-details>)

`adult`, `backdrop_path`, `created_by[]`, **`episode_run_time[]`**, **`first_air_date`**, `genres[]`, `homepage`, `id`, `in_production`, `languages[]`, `last_air_date`, `last_episode_to_air{...}`, `name`, `next_episode_to_air`, `networks[]`, `number_of_episodes`, `number_of_seasons`, `origin_country[]`, `original_language`, `original_name`, `overview`, `popularity`, `poster_path`, `production_companies[]`, `production_countries[]`, `seasons[]`, `spoken_languages[]`, `status`, `tagline`, `type`, `vote_average`, `vote_count`

**영화/TV 스키마 차이 중 반드시 흡수해야 할 것:**

| 개념 | 영화 | TV | 처리 |
| :--- | :--- | :--- | :--- |
| 제목 | `title` | `name` | 도메인에서 하나로 통일 |
| 원제 | `original_title` | `original_name` | 동일 |
| 공개일 | `release_date` | `first_air_date` | 동일 |
| **러닝타임** | `runtime` (정수, 분) | **`runtime` 필드가 없다.** `episode_run_time`(정수 **배열**)뿐 | ⚠️ 아래 참조 |
| IMDb ID | 상세 응답에 `imdb_id` **포함** | 상세 응답에 **없음** → `/3/tv/{id}/external_ids` 필요 | ⚠️ 아래 참조 |

⚠️ **TV의 러닝타임 주의.** `episode_run_time`은 배열이고 비어 있을 수 있으며(회차마다 길이가 다른 시리즈), 시리즈 전체 길이가 아니라 회차 길이다. `last_episode_to_air.runtime`이 있긴 하지만 의미가 또 다르다. 영화의 `runtime`과 같은 컬럼에 넣으면 의미가 오염된다 → **별도 컬럼이거나, 최소한 "회차 길이"임을 이름에 드러내야 한다.**

### 7.4 외부 ID

📄 `GET /3/movie/{movie_id}/external_ids` (<https://developer.themoviedb.org/reference/movie-external-ids>)
→ `id`, `imdb_id`, `wikidata_id`, `facebook_id`, `instagram_id`, `twitter_id`

📄 `GET /3/tv/{series_id}/external_ids` (<https://developer.themoviedb.org/reference/tv-series-external-ids>)
→ `id`, `imdb_id`, `freebase_mid`, `freebase_id`, **`tvdb_id`**, `tvrage_id`, `wikidata_id`, `facebook_id`, `instagram_id`, `twitter_id`

예제 응답에서 `wikidata_id`, `instagram_id`, `twitter_id`가 `null`인 경우가 확인된다 → **전부 nullable로 다룬다.**

### 7.5 `append_to_response`로 요청 수 줄이기

📄 출처: <https://developer.themoviedb.org/docs/append-to-response>

> "`append_to_response` is an easy and efficient way to append extra requests to any top level namespace. The movie, TV show, TV season, TV episode and person detail methods all support a query parameter called `append_to_response`. This makes it possible to make sub requests within the same namespace in a single HTTP request."

```
GET /3/movie/{id}?language=ko-KR&append_to_response=external_ids,translations,images&include_image_language=ko,en,null
```

**최대 20개**까지 (에러 27: "Too many append to response objects: The maximum number of remote calls is 20").

📄 주의: "Each method will still respond to whatever query parameters are supported by each individual request. This is worth pointing out specifically for images since your language parameter will filter images."

→ 이 한 번의 호출로 상세 + 외부 ID + 번역 + 이미지를 모두 받으면 **항목당 4회가 1회로 줄어든다.** 124만 건 규모에서는 이 차이가 곧 배치 소요 시간이다. 40 req/s 한도가 사실상 4배로 늘어나는 효과.

### 7.6 목록 응답(discover)의 필드

📄 `/discover/movie` 예제 결과 항목: `adult`, `backdrop_path`, `genre_ids`, `id`, `original_language`, `original_title`, `overview`, `popularity`, `poster_path`, `release_date`, `title`, `video`, `vote_average`, `vote_count`
📄 `/discover/tv` 예제 결과 항목: `backdrop_path`, `first_air_date`, `genre_ids`, `id`, `name`, `origin_country`, `original_language`, `original_name`, `overview`, `popularity`, `poster_path`, `vote_average`, `vote_count`

→ 목록만으로도 `title`/`overview`/`poster_path`/`release_date`는 채울 수 있다. 다만 **`runtime`, `imdb_id`, `genres`(이름), `tagline`, `status`가 없다.** FE 계약의 `tags`를 장르명으로 채우려면 장르 사전 조인이 필요하고, 내부 컬럼까지 채우려면 결국 상세 호출이 필요하다.

---

## 8. 요약 — 배치 설계에 직결되는 사실들

1. **인증**: `Authorization: Bearer <API Read Access Token>`. `api_key` 쿼리 파라미터와 권한 동일하나, 로그 노출을 피하려면 Bearer가 낫다. 비상업 무료, 귀속 문구 의무.
2. **discover로는 전체 카탈로그를 못 훑는다.** 페이지 상한 500 × 20건 = 10,000건인데 실제 영화는 124만 건(실측). `total_pages`(38,020)를 그대로 믿는 루프는 501페이지에서 400을 맞는다.
3. **증분 수집은 가능하다.** 전량 재수집은 최초 백필 1회로 족하다.
   - **존재 동기화** → 일별 ID 익스포트 (`files.tmdb.org`, 인증 불필요, UTC 08:00 = KST 17:00, 3개월 보존, JSON Lines)
   - **내용 동기화** → `/movie/changes`, `/tv/changes` (24시간 기본, 최대 14일, 100건/페이지)
   - 두 축이 뱉은 ID만 상세 조회.
4. **삭제 감지는 익스포트 diff로만 가능하다.** 변경 API는 삭제를 알려주지 않는다.
5. **rate limit은 약 40 req/s** (근사치, 언제든 변경 가능). 429 + `{success,status_code:25,status_message}`. 일일 한도·동시성 제한은 문서화되어 있지 않다. `Retry-After` 헤더 유무는 **미확인** → 헤더 없이도 동작하는 백오프 필요.
6. **한국어 텍스트 폴백은 없다.** TMDB Staff가 "Language fallbacks are not currently supported"라고 명시(2025-03). 앱이 폴백 체인(`ko` → `en` → `original_*`)을 직접 구현해야 한다. **반면 이미지(`poster_path`)는 문서화된 자동 폴백이 있다.**
7. **이미지는 `secure_base_url` + size + `file_path`.** `/configuration`은 시작 시 1회 캐시. DB에는 완성 URL이 아니라 `file_path`를 저장.
8. **FE 계약의 `ContentDto`는 얇다.** `release_date`/`runtime`/`imdb_id`는 FE에 안 나가는 내부 컬럼. `id`가 UUID이므로 **`tmdbId` + `mediaType` 유니크 제약**이 배치 멱등성의 전제.
9. **`append_to_response`(최대 20개)로 항목당 4회 → 1회.** 이 규모에서는 이것이 배치 소요 시간을 좌우한다.
10. **`sport` 타입은 TMDB로 채울 수 없다.** 별도 소스 필요.

---

## 9. 확인하지 못한 것 (추측하지 않고 남김)

| # | 항목 | 왜 못 했는지 | 확인 방법 |
| :--- | :--- | :--- | :--- |
| 1 | `language=ko-KR`에 한국어 번역이 없을 때 **`title`/`name`이 무엇으로 채워지는가** (원제 / 영어 / 빈 문자열) | 문서에 서술 없음, API 키 없어 실측 불가 | 키 발급 후 `curl .../movie/550?language=ko-KR` 및 마이너 작품 ID로 비교 (5.2에 명령 있음) |
| 2 | `/movie/changes`·`/tv/changes`에 **500페이지 상한이 적용되는가** | 에러 22는 전역 코드로만 존재, 해당 레퍼런스에 명시 없음 | 14일 창으로 호출해 `total_pages` 확인 후 501페이지 요청 |
| 3 | **429 응답의 헤더** (`Retry-After`, `X-RateLimit-*`) | 문서에 서술 없음. 일부러 한도를 넘기지 않음 | 부하 시험은 "be respectful" 요청에 반하므로 권하지 않음. 헤더 없이 동작하는 백오프로 설계 |
| 4 | **일일 요청 한도 / 동시 요청 수 제한**의 존재 여부 | 문서에 언급 자체가 없음 | TMDB API 지원 포럼 문의 |
| 5 | 변경 API가 **삭제된 항목**을 어떤 형태로든 알려주는지 | 문서에 서술 없음 | 익스포트 diff로 대체 가능하므로 우선순위 낮음 |
| 6 | 일별 익스포트 각 줄의 **필드 스키마의 공식 정의** | 문서가 "adult, video, popularity 같은 값"이라고만 서술 | 3.1에 **실측 결과**를 기록해 두었으나, 공식 보증이 아니므로 스키마 변경 가능성 있음 |

---

## 부록: 참조한 1차 출처 목록

**공식 문서 (docs)**
- 인증: <https://developer.themoviedb.org/docs/authentication-application>
- 시작하기: <https://developer.themoviedb.org/docs/getting-started>
- FAQ (무료 조건·귀속·SLA): <https://developer.themoviedb.org/docs/faq>
- Rate Limiting: <https://developer.themoviedb.org/docs/rate-limiting>
- 에러 코드: <https://developer.themoviedb.org/docs/errors>
- 일별 ID 익스포트: <https://developer.themoviedb.org/docs/daily-id-exports>
- 변경 추적: <https://developer.themoviedb.org/docs/tracking-content-changes>
- 언어: <https://developer.themoviedb.org/docs/languages>
- 이미지 기초: <https://developer.themoviedb.org/docs/image-basics>
- 이미지 언어: <https://developer.themoviedb.org/docs/image-languages>
- 리전: <https://developer.themoviedb.org/docs/region-support>
- append_to_response: <https://developer.themoviedb.org/docs/append-to-response>
- 인기도·트렌딩: <https://developer.themoviedb.org/docs/popularity-and-trending>

**API 레퍼런스 (OpenAPI 정의 + 예제 응답 포함)**
- <https://developer.themoviedb.org/reference/discover-movie>
- <https://developer.themoviedb.org/reference/discover-tv>
- <https://developer.themoviedb.org/reference/movie-popular-list>
- <https://developer.themoviedb.org/reference/movie-details>
- <https://developer.themoviedb.org/reference/tv-series-details>
- <https://developer.themoviedb.org/reference/movie-external-ids>
- <https://developer.themoviedb.org/reference/tv-series-external-ids>
- <https://developer.themoviedb.org/reference/changes-movie-list>
- <https://developer.themoviedb.org/reference/changes-tv-list>
- <https://developer.themoviedb.org/reference/movie-changes>
- <https://developer.themoviedb.org/reference/configuration-details>
- <https://developer.themoviedb.org/reference/genre-movie-list>

**TMDB 공식 포럼 (운영진 발언)**
- 언어 폴백 미지원 (Travis Bell, TMDB Staff, 2025-03-14): <https://www.themoviedb.org/talk/67c7890473b7f95da9c2856f>
- 원본 줄거리 옵션 부재 (ticao2, Moderator, 2023-10-14): <https://www.themoviedb.org/talk/65280f6afd63005d7c4a30ba>

**실측 (2026-09-02)**
- `https://files.tmdb.org/p/exports/movie_ids_08_31_2026.json.gz` 등 8종(movie, tv_series, person, collection, tv_network, keyword, production_company, adult_movie) 다운로드 및 첫 줄 확인. `adult_tv_series`·`adult_person`은 문서 기재만 확인하고 실측하지 않음
- `https://api.themoviedb.org/3/movie/550` 무인증 호출 → 401 에러 바디 형태 확인

> 문서 팁: 위 문서 URL 끝에 `.md`를 붙이면 원문 마크다운(OpenAPI 정의 포함)을 그대로 받을 수 있다. 전체 문서 색인은 <https://developer.themoviedb.org/llms.txt>.
