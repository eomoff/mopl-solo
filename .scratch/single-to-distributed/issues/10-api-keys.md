# 외부 API 키 발급

Type: task
Status: resolved
Blocked by: 02

## Question

외부 API 키를 실제로 발급받는다. 두 조사 티켓이 **어떤 티어의 무엇이 필요한지** 알려주면, 그에 맞춰 가입·발급한다.

에이전트가 대신할 수 없는 작업(가입, 약관 동의, 결제수단 등록)이므로 정확한 체크리스트를 만들어 넘긴다.

완료 시 기록할 것:

- 발급된 키의 종류와 **보관 위치** — 환경변수 이름, 로컬 설정 파일 경로. **값은 저장소에 커밋하지 않는다.**
- 티어 제약 — rate limit, 일일 한도.
- 키가 만료되는가, 만료된다면 언제.

이 사실들은 배치 설계와 CI/배포(자격증명 주입)가 의존한다.

---

**키 발급 직후 실행할 검증** ([TMDB API 조사]가 키가 없어 확인하지 못한 것)

- 한국어 번역이 없는 작품에 `language=ko-KR`로 조회했을 때 `title`/`name`이 **무엇을 반환하는가** — 원제인가, 영어인가, 빈 문자열인가. 문서에 없다. 실행할 `curl` 한 줄이 `research/tmdb-api.md` 9장에 있다. 이 답이 배치의 폴백 체인 설계를 바꾼다.

---

**[스포츠 콘텐츠를 목적지에 둘 것인가] 해결로 범위가 축소됨**

무료 티어로 확정되어 결제·구독 단계가 사라졌다. 남은 것은 둘뿐이다.

- **TMDB** — 무료 가입 후 키 발급. Bearer(API Read Access Token) 권장. 비상업적 이용 무료이며 **출처 표기와 로고 노출 의무**가 있다.
- **TheSportsDB** — 공개 테스트 키 `123`을 쓰므로 **발급 절차 자체가 없다.** 실측된 상한(하루 3경기 등)이 그대로 제약이다.

**[콘텐츠 수집 배치 설계]가 확정한 것**

- **TMDB만 발급이 필요하다.** TheSportsDB는 공개 키 `123`을 그대로 쓴다.
- TMDB는 **Bearer(API Read Access Token)** 를 쓴다 — `api_key` 쿼리 파라미터와 권한은 같지만 배치 요청량에서 자격증명이 액세스 로그에 남지 않는다.
- **비상업 무료이며 출처 표기와 로고 노출 의무**가 있다. README에 반영할 항목이다.


## Answer

> **결정 주체**: 환경변수 이름은 에이전트가 정했다. 키 발급·입력은 사용자가 직접 수행했다.

### 확보 상태

| 소스 | 값 | 보관 위치 |
| --- | --- | --- |
| **TMDB** | **API Read Access Token** (v4, `eyJ...`로 시작) — 32자 16진수인 `API Key (v3 auth)`가 아니다 | `MOPL_TMDB_ACCESS_TOKEN` (사용자 IDE 실행 구성. **저장소에 커밋하지 않는다**) |
| **TheSportsDB** | 공개 무료 키 `123` — **비밀이 아니다** | `application.yaml`의 기본값. 유료 전환 시에만 `MOPL_SPORTSDB_API_KEY`로 덮어쓴다 |

프로퍼티 매핑: `mopl.tmdb.access-token`, `mopl.sportsdb.api-key`.

Bearer(Read Access Token)를 고른 근거는 [콘텐츠 수집 배치 설계]에 있다 — `api_key` 쿼리 파라미터와 권한은 같지만 **배치 요청량에서 자격증명이 액세스 로그에 남지 않는다.**

### 티어 제약

**TMDB** — 비상업 무료. **귀속 표기 의무**가 붙는다:

> "This product uses the TMDB API but is not endorsed or certified by TMDB."

애플리케이션의 "About"/"Credits" 성격의 영역에 승인된 로고와 함께 표기해야 한다. **README에 반영할 항목이다.**

rate limit은 약 40 req/s이나 문서가 "근사치이며 언제든 바뀔 수 있다"고 명시하므로 **20~25 req/s로 여유를 둔다.** 일일 한도·동시성 제한은 문서화되어 있지 않다. **SLA가 없다**("We do not currently provide an SLA") — 배치가 TMDB 장애에 물려 통째로 실패하지 않도록 재시도·부분 커밋이 필요하다는 뜻이다.

**TheSportsDB** — 무료 키의 실측 상한이 그대로 제약이다: `eventsday` 전 세계 하루 3건, `all_leagues` 10개(전부 유럽 축구), `eventsseason` 15건. 스로틀은 문서의 30/분이 아니라 실측 43회에서 걸렸고 **429가 Cloudflare 평문**이다.

### 만료

**확인하지 못했다.** 조사가 참조한 1차 문서에 Read Access Token의 만료에 관한 서술이 없었고, 추측하지 않는다. 배치가 갑자기 401을 받기 시작하면 이 항목을 먼저 의심한다.

### 남은 확인 (키가 있어야 가능한 것)

[TMDB API 조사]가 키가 없어 **미확인으로 남긴 항목**이다. 한국어 번역이 없을 때 `title`이 원제로 오는지, 영어로 오는지, 빈 문자열로 오는지 — **이 답에 따라 [콘텐츠 수집 배치 설계]의 폴백 체인 설계가 달라진다.**

```bash
curl -s -H "Authorization: Bearer $MOPL_TMDB_ACCESS_TOKEN" \
  'https://api.themoviedb.org/3/movie/550?language=ko-KR' | jq '{title, original_title, overview, tagline}'
```

토큰이 사용자의 IDE 실행 구성에만 있어 에이전트 셸에서는 실행할 수 없다. **1주차에 확인해 [콘텐츠 수집 배치 설계]에 기록한다.**
