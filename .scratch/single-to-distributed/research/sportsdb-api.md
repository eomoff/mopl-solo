# TheSportsDB API 조사

Ticket: `issues/03-sportsdb-research.md`
조사일: 2026-09-02 (문서 열람 + 무료 키 `123`으로 실제 호출 검증)

## 이 문서를 읽는 법

TheSportsDB는 문서가 얇고, **문서와 실제 응답이 어긋나는 지점이 여러 곳 있다.**
그래서 아래는 두 종류의 근거를 구분해서 적었다.

- **[문서]** — 공식 문서에 적힌 내용. URL 표기.
- **[실측]** — 2026-09-02에 무료 키 `123`으로 직접 호출해 확인한 내용. 문서에 없거나 문서와 다른 것.

**문서에 "마지막 갱신일"이 없다.** 푸터의 `2016 - 2026 TheSportsDB.com`(<https://www.thesportsdb.com/documentation>)이 유일한 시점 단서인데, 이건 자동 갱신되는 저작권 표기지 문서 개정일이 아니다. 따라서 아래 [문서] 항목의 유효 시점은 **내가 읽은 2026-09-02**까지만 보증된다.

---

## 1. 결론 먼저

| 질문 | 답 |
|---|---|
| 무료 키로 배치 수집이 되는가 | **안 된다.** 리그 목록 10개, 하루 이벤트 3건, 시즌 이벤트 15건이 상한. |
| 유료로 가면 되는가 | 된다. **$9/월 (Single Developer)** 이면 충분. |
| 전량 스윕이 가능한가 | 가능하지만 **축을 정해 좁혀야 한다.** "전체 이벤트" 엔드포인트는 없다. |
| 증분 수집이 되는가 | **안 된다.** changed-since / updated-at 질의 수단이 전무하다. |
| 영화·드라마와 하나의 콘텐츠 모델로 합칠 수 있는가 | **필드는 맞출 수 있으나 의미는 맞지 않는다.** 3절과 5절 참고. 프론트 계약이 이미 합치도록 강제하고 있어서, 결정할 것은 "합칠지"가 아니라 **"합친 자리에서 스포츠의 무엇을 버릴 것인가"**다. |

---

## 2. 키 발급과 티어

### 접근 모델이 바뀌어 왔다는 점부터

문서가 이 변화를 자기 입으로 인정한다:

> "The site originally had all methods available to use under the free API with no limits, but sadly it became too popular and was abused. So over the years we have had to limit certain methods, while trying to keep the core functionality."
> — <https://www.thesportsdb.com/documentation>

즉 **인터넷에 돌아다니는 예전 글·튜토리얼의 무료 티어 설명은 대부분 이미 틀렸다.** 아래 수치는 2026-09-02 기준 실측이다.

**Patreon은 현재 주 경로가 아니다.** 과거에는 Patreon 후원으로 프리미엄 키를 받았고, 결제 페이지에도 그 안내가 있었으나 지금은 **HTML 주석으로 비활성화**되어 있다. 원문 HTML(<https://www.thesportsdb.com/pricing>) 478행:

```html
<!-- 2nd row
<div class="row"><div class="center">
  <br>No Paypal account? Try our <a href="https://www.patreon.com/thedatadb">patreon</a> instead.
</div></div>-->
```

→ **현재 결제는 사이트 자체(PayPal/카드)로 일원화되어 있고, Patreon 링크는 페이지에 렌더되지 않는다.** 티켓 발주 시 "Patreon 티어"라고 적었다면 그 전제를 갱신해야 한다. (다만 `patreon.com/thedatadb` 계정 자체가 폐쇄되었는지는 확인하지 못했다 — 마크업에서 제거되었다는 것만 확인했다.)

### 티어와 가격 [문서]

출처: <https://www.thesportsdb.com/pricing>, <https://www.thesportsdb.com/docs_pricing.php?billing=annual>, <https://www.thesportsdb.com/docs_pricing.php?billing=lifetime>

| 티어 | 월 | 연 | 평생 | rate limit | 비고 |
|---|---|---|---|---|---|
| Free | $0 | — | — | 30 req/min | "Most queries limited" |
| Single Developer | $9 | $90 | $295 | 100 req/min | V2 API, 2분 라이브스코어, YouTube 하이라이트, 전용 키 |
| Small Business | $20 | $200 | $999 | 120 req/min | "No limit on returned data", 전용 이메일 지원, Private API Key |

### 무료 키 = `123` (발급 절차 없음) [문서 + 실측]

> "The current free API key is: 123"
> — <https://www.thesportsdb.com/documentation>

가입 없이 URL 경로에 그대로 박아 쓴다: `https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=Arsenal`
프리미엄 키는 결제 후 사용자 프로필 페이지에서 확인한다 (<https://www.thesportsdb.com/free_sports_api>).

### 무료 키의 실제 한계 — 이게 핵심이다

문서는 메서드마다 `Free Limit` / `Premium Limit`을 적어두는데, 이건 rate limit이 아니라 **응답 배열의 최대 길이**다. 직접 호출해 전부 확인했다:

| 호출 | 문서상 Free / Premium | **실측 (무료 키)** |
|---|---|---|
| `all_leagues.php` | 10 / 3000 | **10건** — 전부 유럽 축구 (EPL, 챔피언십, 스코틀랜드, 분데스리가, 세리에A, 리그1, 라리가, 그리스, 에레디비시, 벨기에) |
| `eventsday.php?d=2026-09-01` | 3 / 1500 | **3건** — 그날 전 세계 전 종목 통틀어 3건 |
| `eventsseason.php?id=4328&s=2024-2025` | 15 / 3000 | **15건** — EPL 한 시즌은 380경기이므로 **약 4%** |
| `eventsnextleague.php?id=4328` | 1 / 20 | **1건** |
| `eventspastleague.php?id=4328` | 1 / 20 | **1건** |

`searchteams.php`는 아예 문서에 이렇게 박혀 있다:

> "NOTE: Free tier limited to just \"Arsenal\". Upgrade for full search."
> — <https://www.thesportsdb.com/documentation>

**판정: 무료 키는 배치 수집용으로 쓸 수 없다.** 리그 목록조차 10개만 나오므로 "리그를 열거해서 순회한다"는 스윕 전략의 첫 단계부터 막힌다. 개발 중 스키마 확인·파서 작성용으로는 충분하지만, 실제 수집 Job을 돌리려면 **$9/월 Single Developer가 사실상 최소 요건**이다.

### V1 vs V2 [문서]

- V1: `https://www.thesportsdb.com/api/v1/json/{APIKEY}/...` — 키를 URL 경로에 넣음. 무료 키로 접근 가능.
- V2: `https://www.thesportsdb.com/api/v2/json/...` — 키를 **`X-API-KEY` 헤더**로 전송. **프리미엄 전용.**

> "V2 is only for premium subscribers and will be the only version developed going forward."
> — <https://www.thesportsdb.com/documentation>

[실측] 키 없이 V2를 호출하면 **HTTP 400**이 돌아온다 (401/403이 아니다).

→ **V2가 앞으로의 유일한 개발 대상이라고 공식 선언되어 있다.** 유료를 쓸 거라면 처음부터 V2로 붙는 편이 낫다. 다만 이번 조사에서 V2를 실호출로 검증하지는 못했다 (키 없음). V2에 대한 아래 서술은 문서와 공개된 정적 예제 JSON(`https://www.thesportsdb.com/api/v2/examples/*.json`)에만 근거한다.

---

## 3. 이벤트 목록 엔드포인트 — 어떤 축이 있는가

출처: <https://www.thesportsdb.com/documentation> (v1 API Schedule / v2 API Schedule 절)

### V1

| 축 | 엔드포인트 | Free/Premium 상한 |
|---|---|---|
| **날짜** | `eventsday.php?d={YYYY-MM-DD}` (선택: `&s={sport}`, `&l={idLeague}`) | 3 / 1500 |
| **시즌 × 리그** | `eventsseason.php?id={idLeague}&s={strSeason}` | 15 / 3000 |
| **리그 (다음)** | `eventsnextleague.php?id={idLeague}` | 1 / 20 |
| **리그 (지난)** | `eventspastleague.php?id={idLeague}` | 1 / 20 |
| **팀 (다음)** | `eventsnext.php?id={idTeam}` | 1 / 10 — 문서 주: `*free key only shows home event` |
| **팀 (지난)** | `eventslast.php?id={idTeam}` | 1 / 10 — 같은 주석 |
| **TV 편성** | `eventstv.php?d=...&s=...&a=...&c=...&id=...` | 1 / 1500 |
| 단건 | `lookupevent.php?id={idEvent}` | 1 / 1 |
| 문자열 검색 | `searchevents.php?e={strEvent}&s=&d=&f=` | 1 / 10 |

### V2 (프리미엄 전용)

- `/api/v2/json/schedule/league/{idLeague}/{season}` — **상한 3000. 이게 대량 수집의 주력이다.**
- `/api/v2/json/schedule/next/league/{idLeague}` · `/previous/league/{idLeague}` — 각 10
- `/api/v2/json/schedule/next/team/{idTeam}` · `/previous/team/{idTeam}` — 각 10
- `/api/v2/json/schedule/full/team/{idTeam}` — 250
- `/api/v2/json/schedule/next/venue/{idVenue}` · `/previous/venue/{idVenue}` — 각 10 (V1에 없는 축)
- `/api/v2/json/livescore/{sport|idLeague|all}` — 2분 주기 라이브스코어

[실측] 정적 예제 `https://www.thesportsdb.com/api/v2/examples/full_league_season_schedule.json`는 **EPL 2023-2024 한 시즌 380경기를 한 응답에 전부** 담고 있다. 페이지네이션 파라미터는 문서 어디에도 없다 — 상한(3000)까지는 통째로 오고, 넘으면 어떻게 되는지는 **문서에 없고 확인하지 못했다.**

### 전량 스윕은 가능한가 — **가능하지만 축을 고정해야 한다**

**"모든 이벤트"를 반환하는 엔드포인트는 없다.** 문서에 열거된 v1·v2 전체 메서드 목록을 훑어 확인했다. 스윕하려면 다음 둘 중 하나로 좁혀야 한다:

**(A) 리그 × 시즌 순회** — 카탈로그를 채우는 용도
```
all_leagues.php  (프리미엄 3000개)
  → search_all_seasons.php?id={idLeague}   (리그별 시즌 목록, 프리미엄 500)
    → eventsseason.php?id={idLeague}&s={season}   (프리미엄 3000)
```
전체를 다 돌면 리그 3000개 × 시즌 N개이므로 **호출 수가 수만 건**이 된다. 100 req/min이면 수 시간~수일. → **리그 화이트리스트를 두는 게 사실상 필수.**

**(B) 날짜 순회** — 최신분을 따라가는 용도
```
eventsday.php?d={날짜}   (프리미엄 1500, 전 종목)
```
하루 1콜이면 되므로 훨씬 싸다. **1년치가 365콜**. 증분 수단이 없는 상황에서 이게 가장 현실적인 정기 수집 축이다.

> **배치 설계(09) 함의**: 초기 적재는 (A)로 리그 화이트리스트를 돌고, 정기 Job은 (B)로 "오늘 ± N일"만 다시 긁는 형태가 자연스럽다. 스포츠는 경기 종료 후 스코어가 채워지므로 과거 며칠도 다시 봐야 한다.

---

## 4. 증분 수집과 rate limit

### 증분 수집: **불가능하다** (수단이 없다)

문서에 열거된 v1·v2 전체 메서드를 확인했으나:

- `changes` / `updated_since` / `modified_after` 류의 엔드포인트가 **없다.**
- 이벤트 응답 어디에도 `dateUpdated` / `lastModified` 같은 **변경 시각 필드가 없다.** (5절 필드 목록 참고)

유일하게 `updated`라는 이름의 필드가 존재하는 곳은 **프리미엄 전용 Livescore 피드**다:

> `[updated] => // date and time stamp of the API feed`
> — <https://www.thesportsdb.com/docs_api_data>

실제 값(`https://www.thesportsdb.com/api/v2/examples/livescore_all.json`): `"updated": "2025-05-26 20:32:22"`.
그러나 이건 **지금 진행 중인 경기의 스냅샷**일 뿐, "이 시각 이후 바뀐 것을 달라"는 **질의 수단이 아니다.** 증분 API로 쓸 수 없다.

→ **중복 제거는 우리 쪽에서 해야 한다.** `idEvent`가 안정적인 전역 고유 ID이므로 여기에 unique 제약을 걸고 upsert 하는 것이 유일한 길이다. (`idEvent`는 v1·v2·livescore 응답에 모두 동일하게 등장한다 — 실측 확인.)

### Rate limit [문서 + 실측 — 문서와 다르다]

[문서] <https://www.thesportsdb.com/documentation>:
> "Free users 30 requests per minute. Premium 100 per minute. Business 120 per minute."
> "You will recieve a \"429\" http header if you breach the limit, then you will need to wait another minute until requests will work again."

[실측] 무료 키로 초당 연속 호출한 결과:
- **42번째 요청까지 200**, 43번째부터 429. → 문서의 30/min과 실제가 다르다. 무료 키는 **공유 버킷**이라 그 순간 남의 사용량에 따라 실효 한도가 흔들린다고 보는 게 맞다. **문서 수치를 신뢰하고 그 언저리까지 밀어붙이면 안 된다.**
- 429 응답이 **JSON이 아니다.** Cloudflare가 앞단에서 끊는다:
  ```
  HTTP/2 429
  content-type: text/plain; charset=UTF-8
  retry-after: 119
  server: cloudflare

  error code: 1015
  ```

> **⚠️ 배치 설계에 직결되는 함정**: 429 본문이 `error code: 1015`라는 **평문**이다. 응답 코드를 확인하지 않고 바로 JSON 역직렬화하면 파싱 예외가 터지고, 로그에는 "rate limit"이 아니라 "malformed JSON"으로 남는다. **상태코드와 `Content-Type`을 먼저 보고 분기해야 한다.**
> 또한 `Retry-After: 119` — 문서는 "1분 기다리면 된다"지만 **실제로는 약 2분**을 요구한다. 재시도 백오프는 문서가 아니라 `Retry-After` 헤더를 따라야 한다.

---

## 5. 스키마 — 그리고 하나의 콘텐츠 모델로 합칠 수 있는가

### 5-1. 이벤트가 실제로 반환하는 필드 [실측]

`eventsday.php` 기준 **49개 필드** (2026-08~10 사이 25일치 75건 + EPL 2024-2025 15건, 총 90건 표본):

```
idEvent  idAPIfootball  strEvent  strEventAlternate  strFilename  strSport
idLeague  strLeague  strLeagueBadge  strSeason  intRound  strGroup
strHomeTeam  strAwayTeam  idHomeTeam  idAwayTeam  strHomeTeamBadge  strAwayTeamBadge
intHomeScore  intAwayScore  intHomeScoreExtra  intAwayScoreExtra  intScore  intScoreVotes
strTimestamp  dateEvent  dateEventLocal  strTime  strTimeLocal
idVenue  strVenue  strCountry  strCity  intSpectators  strOfficial  strWeather
strDescriptionEN  strResult  strStatus  strPostponed  strLocked
strPoster  strSquare  strThumb  strBanner  strFanart  strMap  strTweet1  strVideo
```

### 5-2. 프론트 계약(`api.json`)의 콘텐츠 스키마

`project-mopl-fe-1.0.5/api.json`의 `ContentDto`:

| 필드 | 타입 | required |
|---|---|---|
| `id` | uuid | ✅ |
| `type` | enum: **`movie` \| `tvSeries` \| `sport`** | ✅ |
| `title` | string | ✅ |
| `description` | string | ✅ |
| `thumbnailUrl` | string | ✅ |
| `tags` | string[] | ✅ |
| `averageRating` | double | ✅ |
| `reviewCount` | int32 | ✅ |
| `watcherCount` | int64 | ✅ |

**계약이 이미 답을 정해놓았다.** `type` enum에 `sport`가 movie·tvSeries와 **나란히** 들어 있고, `GET /api/contents`는 `typeEqual=sport`로 같은 목록을 필터링하며, 정렬 축은 `createdAt | watcherCount | rate` 셋뿐이다.
→ **"합칠까 말까"는 우리가 결정할 수 있는 게 아니다. 계약이 단일 `Content` 테이블/DTO를 강제한다.**

### 5-3. 그래서 매핑은 되는가 — 필드별 실측 충족률

| 계약 필드 | TheSportsDB 대응 | 90건 표본 실측 | 판정 |
|---|---|---|---|
| `title` | `strEvent` (예: "Liverpool vs Swansea") | **90/90 채워짐** | ✅ 문제 없음 |
| `thumbnailUrl` | `strThumb` | **79/90 non-empty (88%)**, 11건 `""` | ⚠️ 폴백 필요 → `strLeagueBadge`(90/90 채워짐)로 대체 가능 |
| `description` | `strDescriptionEN` | **90건 중 1건** (75건에만 필드가 존재, 그중 65건 `""`, 9건 `null`) | ❌ **사실상 없다** |
| `tags` | 대응 없음 — `strSport`/`strLeague`/`strCountry`를 태그로 합성해야 함 | — | ⚠️ 파생 필요 |
| `averageRating` / `reviewCount` / `watcherCount` | 대응 없음 (우리 서비스가 생성) | — | ✅ 무관 |

`strDescriptionEN`이 채워진 유일한 1건은 NFL 프리시즌 경기였고, 내용은 명백히 생성형 홍보문이었다("The historic opening chapter of the NFL pre-season takes center stage under the celebratory lights of…"). **일반적인 경기에는 설명이 없다고 보는 게 맞다.**

그런데 `ContentDto.description`은 **required**다.
→ **"Liverpool vs Swansea · 잉글랜드 프리미어리그 · 2014-12-29 · Anfield" 같은 문자열을 우리가 합성해 채워야 한다.** 원본에서 가져오는 게 아니라 만드는 것이다.

### 5-4. 구조적으로 다른 지점 — 여기가 진짜 문제다

필드 매핑은 위처럼 어떻게든 된다. 문제는 **의미**다.

**(a) 계약에 날짜 필드가 아예 없다.**
`ContentDto`에는 `releaseDate`도 `startsAt`도 없다. 그런데 **스포츠 경기의 정체성은 "언제 열리는가"다.** `strTimestamp`(90/90 채워짐)를 넣을 자리가 계약에 없다.
정렬 축도 `createdAt | watcherCount | rate`뿐이라 **"다가오는 경기 순"으로 정렬할 수 없다.** 영화는 개봉일이 없어도 목록이 성립하지만, 경기 목록에서 킥오프 시각이 빠지면 목록 자체가 무의미해진다.

**(b) 영화·드라마는 지속적으로 존재하는 작품이고, 경기는 특정 시각에 한 번 벌어지는 사건이다.**
- 영화는 1년 뒤에도 볼 수 있다. 경기는 끝나면 끝난다.
- 경기는 **끝난 뒤 데이터가 바뀐다** — 실측에서 `intHomeScore`/`intAwayScore`가 90건 중 **46건이 `null`**(미래 경기), 44건만 채워져 있었다. 즉 같은 `idEvent` 레코드가 시간이 지나면서 스코어·상태가 갱신된다. 영화 레코드에는 없는 성질이다.
- `strStatus` 실측 값: `NS`(미시작) / `FT`(종료) / `CANC`(취소) / `""` / `null`. **취소·연기되는 콘텐츠**라는 개념 자체가 영화·드라마에 없다.

**(c) "같은 콘텐츠를 보는 사람끼리 어울린다"는 이 서비스의 핵심 기능과는 오히려 잘 맞는다.**
`watcherCount`, `watching-sessions`는 **동시 시청**을 전제로 하는데, 스포츠 생중계야말로 동시 시청의 원형이다. 이 축에서는 스포츠가 영화보다 계약에 더 잘 맞는다.

**(d) 종목에 따라 "홈 vs 원정" 구조조차 성립하지 않는다.**
실측 90건 중 5건은 `strHomeTeam`/`strAwayTeam`이 **`null`**이었다 — 육상(European Athletics Championships "Mens 5000 metres Final"), 배드민턴(BWF World Tour "Korea Masters 2026"). 개인 종목·토너먼트는 대진 구조가 없다.
→ 스포츠 안에서도 단일 구조가 아니다. **`strEvent`(제목)만이 전 종목에 걸쳐 신뢰할 수 있는 유일한 서술 필드다.**

### 5-5. 판정

> **하나의 `Content` 모델로 합치는 것은 가능하고, 계약상 강제된다. 다만 그것은 "스포츠를 영화의 모양으로 눌러 담는" 것이다.**
>
> `title` / `thumbnailUrl`까지는 자연스럽게 대응되지만, `description`은 우리가 합성해야 하고, **경기 시각·스코어·경기 상태(예정/종료/취소)는 계약에 넣을 자리가 없다.**
>
> 권고하는 형태: **`Content`는 계약대로 단일 유지하되, 스포츠 고유 속성(`strTimestamp`, `idHomeTeam`/`idAwayTeam`, 스코어, `strStatus`, `idLeague`, `strSeason`)은 별도 테이블/필드로 분리해 `Content`에 매단다.** 그러면 프론트 계약을 깨지 않으면서도 "오늘 경기" 같은 스포츠다운 질의가 가능해진다.
>
> 반대로, 계약에 없다는 이유로 경기 시각을 아예 버리면 **스포츠 콘텐츠는 목록에서 의미를 잃는다.** 이건 05(도메인 모델) 티켓에서 명시적으로 결정하고 넘어가야 할 지점이다.

---

## 6. 데이터 안정성 — 방어가 필요한 지점들

전부 90건 표본 실측 기준이다. **결론: 방어가 반드시 필요하다.**

### (1) 같은 이벤트인데 **엔드포인트마다 필드 개수가 다르다** ⚠️ 가장 위험

- `eventsday.php` → **49개 필드**
- `eventsseason.php` → **30개 필드**

`eventsseason.php` 응답에는 다음 19개 필드가 **키 자체가 존재하지 않는다**:
```
strDescriptionEN  idVenue  strCity  intSpectators  strOfficial  strWeather  strResult
strLocked  strGroup  idAPIfootball  intScore  intScoreVotes
intHomeScoreExtra  intAwayScoreExtra  strBanner  strFanart  strSquare  strMap  strTweet1
```
V2의 `full_league_season_schedule`은 더 얇아서 **20개 필드**뿐이다 — 380건 전수 확인. `strThumb`·`strVenue`(문자열)는 있으나 `strDescriptionEN`·`idVenue`·`strPoster`·`idLeague`·`strLeague`·`strSeason`이 **없다**.

→ **DTO를 "누락 = null"로 관대하게 짜야 한다.** 그리고 시즌 스윕(A)으로만 적재하면 설명·경기장 ID를 **영영 못 받는다.** 두 축을 섞어 쓸 거면 이 차이를 알고 설계해야 한다.

### (2) 숫자가 전부 **JSON 문자열**로 온다

```json
"intHomeScore": "4",  "intSpectators": "44621",  "intRound": "19",  "idEvent": "441613"
```
표본 전체에서 `int*`/`id*` 필드가 JSON number로 온 경우는 **0건**이다. Jackson 역직렬화 시 `int`/`Long`으로 받으면 관대 모드에서는 통과하지만, 값이 `""`인 경우 폭발한다. **String으로 받고 우리 쪽에서 파싱하는 편이 안전하다.**

### (3) "없음"을 `null`과 `""`로 **일관성 없이** 섞어 쓴다

같은 필드가 레코드마다 다르다. 실측 (present / null / `""` / 값있음):

| 필드 | null | `""` | 값 |
|---|---|---|---|
| `strDescriptionEN` | 9 | 65 | **1** |
| `strCity` | 7 | 26 | 42 |
| `strOfficial` | 14 | 61 | 0 |
| `strWeather` | 14 | 61 | 0 |
| `strGroup` | 9 | 52 | 14 |
| `strResult` | 2 | 22 | 51 |
| `strTimeLocal` | 9 | 3 | 78 |
| `strStatus` | 2 | 3 | 85 |
| `strVideo` | 9 | 39 | 42 |

→ **`null` 체크만으로는 부족하다.** `isBlank()` 수준의 정규화를 파서 진입점에서 일괄 적용해야 한다.

### (4) `intRound`는 **순수한 회차 번호가 아니다** — 특수값이 섞인다

문서(<https://www.thesportsdb.com/docs_api_data>)의 Rounds 표:
```
125 = Quarter-Final   150 = Semi-Final   160 = Playoff   170 = Playoff Semi-Final
180 = Playoff Final   200 = Final        400 = Qualifier  500 = Pre-Season
```
실측 표본에서 `200`(결승)과 `500`(프리시즌)이 `1`~`26` 같은 실제 회차와 **같은 필드에** 섞여 나왔다. **정수 회차로 취급하면 "500라운드"가 생긴다.**

### (5) `strSeason` 포맷이 리그마다 다르다

실측: `"2024-2025"`(EPL) / `"2026"`(아르헨티나 프리메라) / `"2026-2027"`.
→ `eventsseason.php?s=` 에 넘길 시즌 문자열을 **추측할 수 없다.** 반드시 `search_all_seasons.php?id={idLeague}`로 먼저 리그별 시즌 목록을 받아야 한다.

### (6) 로컬 시각 필드를 믿지 마라

실측 예: 같은 `strTimestamp: "2026-09-01T00:15:00"`인 두 아르헨티나 경기가
- 경기 A → `dateEventLocal: 2026-08-31`, `strTimeLocal: 21:15:00` (UTC-3, 맞음)
- 경기 B → `dateEventLocal: 2026-09-01`, `strTimeLocal: 16:00:00` (**틀림**)

→ **`strTimestamp`(UTC, 90/90 채워짐)만 신뢰하고, 로컬 변환은 우리가 한다.**

### (7) 문서가 실제 응답과 어긋난다

- `docs_api_data`는 `strStatus`를 `(empty/unused)`라고 적어놨지만, 실측에서 **85/90건이 `FT`/`NS`/`CANC`로 채워져 있다.**
- `docs_api_testing`은 "As you can see we are using the free API key here **'3'**"이라 적었는데, 바로 위 URL은 `/123/`을 쓴다. (<https://www.thesportsdb.com/docs_api_testing>)
- `docs_api_data`의 Livescore 설명은 **1인칭 초고 상태**다: *"not yet sure of pre-game or overtime yet as I am still building & testing"*, *"appears to be dynamic/changes (?)"*.
- `documentation`의 v2 Schedule 절은 제목이 "Next **10** Events in League"인데 설명은 "List the next **5** events"라 적혀 있다.

→ **이 API의 문서는 규범이 아니라 참고자료로 취급해야 한다.** 실제 응답으로 검증하는 것이 전제다.

### (8) 한국어 지원이 **전혀 없다**

- 이벤트에는 `strDescriptionEN` **하나뿐**이다. 다른 언어 필드가 없다.
- 리그 엔티티에는 15개 언어 설명 필드가 있으나(`EN DE FR IT CN JP RU ES PT SE NL HU NO PL IL`) — **한국어(`strDescriptionKR`)는 없다.** 실측 확인.
- 팀·경기 이름도 전부 영문이다.

→ TMDB의 `language=ko-KR` 같은 로케일 수단이 없다. **스포츠 콘텐츠는 영문으로 노출되거나, 우리가 별도로 한글화해야 한다.** (02 TMDB 조사와 대비되는 지점.)

---

## 7. 확인하지 못한 것 (명시)

정직하게 기록한다. 아래는 **추측하지 않았고, 확인도 못 했다.**

1. **프리미엄/비즈니스 티어의 실제 동작 전부.** 유료 키가 없어 문서 기재값(100 req/min, Free/Premium Limit 수치)을 실측 검증하지 못했다. 무료 티어에서 문서와 실제가 어긋난 사례가 여럿이므로(§4, §6-7), **유료 전환 후 반드시 재측정해야 한다.**
2. **V2 API 실호출.** 키 없이는 HTTP 400. V2 관련 서술은 문서 + 공개 정적 예제 JSON에만 근거한다.
3. **응답 상한(3000 등)을 초과하는 경우의 동작.** 잘리는지, 페이지네이션 수단이 생기는지 문서에 없다. 페이지네이션 파라미터는 문서 어디에도 없다.
4. **`strDescriptionEN` 충족률의 대표성.** 무료 키가 하루 3건으로 잘라내므로 표본이 편향됐을 수 있다. 프리미엄에서 인기 리그만 보면 더 나을 가능성을 배제하지 못한다. 다만 **v1 `eventsseason`과 v2 season schedule은 이 필드를 아예 반환하지 않으므로**, 대량 수집 경로에서는 어차피 못 받는다.
5. **문서 개정일.** 공개되지 않는다. 푸터 저작권(`2016 - 2026`)이 유일한 단서다.
6. **`patreon.com/thedatadb` 계정의 현재 상태.** 결제 페이지 마크업에서 주석 처리되었다는 것만 확인했고, 계정 자체를 열어보지는 않았다.
7. **`idAPIfootball`의 의미.** 외부 API-Football ID로 보이나 문서에 설명이 없다. 실측 75건 중 24건 `null`.
8. **데이터 출처·갱신 주기·SLA.** 사이트가 커뮤니티 편집("Contribute Guide", "Apply Editor", "Missing Scores", "Missing Artwork" 메뉴 존재)을 전제로 하나, **데이터 정확성이나 갱신 보증에 대한 공식 서술을 찾지 못했다.**

---

## 8. 09(배치)·05(도메인 모델)·10(키 발급) 티켓으로 넘길 것

**10 (키 발급)**
- **$9/월 Single Developer 결제 필요.** 무료 키로는 Job이 성립하지 않는다(§2).
- 결제는 사이트 자체(PayPal/카드). **Patreon 경로는 현재 비활성.**
- 키는 결제 후 사용자 프로필에서 확인. **만료 여부는 문서에 없음 — 구독 해지 시 어떻게 되는지 확인 필요.**
- 환경변수 예: `THESPORTSDB_API_KEY`. V1은 URL 경로, V2는 `X-API-KEY` 헤더.

**09 (배치)**
- **증분 API 없음** → `idEvent` unique 제약 + upsert가 유일한 중복 제거 수단.
- 초기 적재는 리그 화이트리스트 × 시즌, 정기 Job은 `eventsday`로 "오늘 ± N일" 재수집(경기 후 스코어가 채워지므로 과거도 다시 봐야 함).
- **429는 JSON이 아니라 평문 `error code: 1015`** → 역직렬화 전에 상태코드 분기 필수. 백오프는 `Retry-After` 헤더를 따를 것(문서의 "1분"이 아니라 실측 119초).
- 문서상 rate limit을 신뢰하지 말 것 — 실측에서 어긋났다.
- 엔드포인트마다 필드 개수가 다르므로 DTO는 전부 nullable.

**05 (도메인 모델)**
- 프론트 계약이 단일 `Content`를 **강제**한다(`type: movie|tvSeries|sport`).
- 결정해야 할 것: **경기 시각·스코어·상태를 어디에 둘 것인가.** `ContentDto`에 자리가 없다. 별도 테이블로 분리하지 않으면 스포츠 콘텐츠는 목록에서 의미를 잃는다.
- `description`은 원본에 없으므로 **합성 규칙을 정해야 한다**(§5-3).
- 한국어가 전혀 없다 — 노출 언어 정책 결정 필요(§6-8).

---

## 출처 목록

- API 문서 (v1·v2 전체 메서드, 인증, rate limit, Free/Premium 상한): <https://www.thesportsdb.com/documentation>
- 가격 (월/연/평생, Patreon 주석 처리 포함): <https://www.thesportsdb.com/pricing> · <https://www.thesportsdb.com/docs_pricing.php?billing=annual> · <https://www.thesportsdb.com/docs_pricing.php?billing=lifetime>
- 무료 API 안내: <https://www.thesportsdb.com/free_sports_api>
- API 데이터 가이드 (Livescore 필드, Event Status 코드, Rounds 특수값): <https://www.thesportsdb.com/docs_api_data>
- API 테스트 가이드 (V2 헤더 인증): <https://www.thesportsdb.com/docs_api_testing>
- V2 정적 예제 JSON: <https://www.thesportsdb.com/api/v2/examples/full_league_season_schedule.json> · <https://www.thesportsdb.com/api/v2/examples/livescore_all.json>
- 프론트 계약: `project-mopl-fe-1.0.5/api.json` (`ContentDto`, `ContentSummary`, `GET /api/contents`)
- 실측 호출: 무료 키 `123`, 2026-09-02, 표본 90건 (`eventsday.php` 25일치 75건 + `eventsseason.php?id=4328&s=2024-2025` 15건)
