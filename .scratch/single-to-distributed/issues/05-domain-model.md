# 도메인 모델 확정

Type: grilling
Status: resolved
Blocked by: 01

## Question

프론트 계약(`api.json`)으로부터 **유도되는 것**과, 내가 **직접 내려야 하는 결정**을 가른다.

- DTO 필드는 계약이 고정한다. 그렇다면 **엔티티는 DTO와 얼마나 같아야 하는가** — 계산 필드(시청자 수, 평균 평점, 구독자 수, 읽지 않음 여부)를 저장할 것인가 조회 시 파생할 것인가. 파생이 맞다면 대규모 트래픽 전제와 어떻게 화해시킬 것인가.
- **PK 타입** — UUID인가 시퀀스인가. 계약이 이미 정해두었는가.
- **시간 타입과 타임존** — 글로벌 서비스가 전제다.
- **애그리거트 경계** — `domain/` 아래 이미 잡아둔 10개 패키지(`auth`, `content`, `conversation`, `directmessage`, `follow`, `notification`, `playlist`, `review`, `user`, `watchingsession`)가 맞는 분할인가. 특히 `conversation`과 `directmessage`를 나눈 근거, `auth`와 `user`를 나눈 근거.
- **콘텐츠 모델의 통합** — 영화·드라마·스포츠 경기를 한 테이블에 담을 것인가, 타입별로 나눌 것인가. (The Sports DB 조사 결과가 이 판단을 바꿀 수 있다.)
- **삭제 정책** — 소프트 삭제가 필요한 것은 무엇인가. 탈퇴한 사용자의 리뷰, 삭제된 플레이리스트의 구독.
- **unique 제약** — 팔로우 중복, 구독 중복, 같은 콘텐츠에 두 번 평점.

---

**[TMDB API 조사]가 이 티켓에 넘긴 제약**

- 프론트 `ContentDto`에는 개봉일·러닝타임·외부 ID를 담을 자리가 없다 → 이 필드들은 **내부 전용 컬럼**이 된다. 계약에 없는 컬럼을 얼마나 둘 것인지가 결정 사항.
- `ContentDto.id`가 UUID이므로 외부 ID는 별도 컬럼이 되고, **`tmdbId` + `mediaType`에 unique 제약이 없으면 배치 재실행마다 행이 중복된다.**
- `averageRating`·`reviewCount`는 TMDB의 `vote_average`가 아니라 **이 서비스 자체 데이터**다. 외부에서 받은 평점을 함께 저장할 것인가?
- TV에는 `runtime`이 없고 `episode_run_time[]` 배열뿐이며, 상세 응답에 `imdb_id`도 없다. **영화·TV·스포츠를 한 콘텐츠 모델로 합칠 때 첫 번째로 부딪히는 지점.**


**[The Sports DB API 조사]가 이 티켓에 넘긴 제약**

- **단일 `Content` 모델은 선택이 아니라 계약이다** — `ContentDto.type`이 `movie | tvSeries | sport` required enum이고 `GET /api/contents`가 `typeEqual`로 셋을 함께 필터한다. 결정할 것은 "합칠까"가 아니라 **"합친 안에서 타입별 고유 속성을 어디에 둘까"**다.
- **계약에 날짜 필드가 없다.** 경기 시작 시각(`strTimestamp`)도, 영화 개봉일도 놓을 자리가 없다. 정렬 축은 `createdAt | watcherCount | rate`뿐. 내부 컬럼으로 두되 노출하지 않을 것인가, 계약을 넘어설 것인가.
- **경기는 생성 후 변한다**(점수 확정, 취소). 영화·드라마에는 없는 가변성이다. 콘텐츠를 불변에 가깝게 다룰 수 있다는 가정이 깨진다.
- `description`이 required인데 스포츠는 실측 90건 중 1건만 채워져 있다. **합성 규칙을 어디서 적용할 것인가**(수집 시점 저장 vs 조회 시점 생성).
- 스포츠끼리도 균질하지 않다 — 홈/원정 팀이 null인 종목이 있다(육상·배드민턴).


## Answer

### 계약이 이미 정한 것 (결정이 아니라 제약)

- **PK는 UUID.** 모든 DTO의 `id`가 `string/uuid`다.
- **시간은 `date-time`.** 글로벌 서비스 전제이므로 타임존을 보존하는 타입을 쓴다.
- **콘텐츠는 단일 모델**, `type: movie | tvSeries | sport` required enum.
- **한 사용자는 최대 하나의 시청 세션** (`GET /api/users/{watcherId}/watching-sessions`가 단건 반환).

### Q1. `watcherCount` 정렬 — **(a) DB 비정규화 컬럼**

`GET /api/contents`의 `sortBy`에 **`watcherCount`가 있다.** 커서 페이지네이션·`totalCount`와 함께다. Redis에 있는 값으로는 DB 테이블을 정렬하고 페이지를 끊을 수 없다.

**`contents.watcher_count`를 비정규화 컬럼으로 둔다.** 정렬만이 아니라 `ContentDto`에 required라 **모든 목록 응답의 모든 항목**에 필요하다는 점이 함께 작용했다 — 컬럼이면 그냥 셀렉트고, Redis면 페이지마다 조회가 붙는다.

**이 결정은 [분산 전환 이음매 확정]의 1번을 수정한다.** Redis의 역할이 "시청 세션의 소스 오브 트루스"에서 **"실시간 세션 레지스트리, 집계값은 DB에 비정규화"**로 바뀐다. 해당 티켓에 수정 사항을 기록했다.

대가는 **같은 값이 두 곳에 있다는 것**이다. 연결 비정상 종료나 Redis 재시작으로 어긋날 수 있으므로 **주기적 재계산이 필요하다** → [콘텐츠 수집 배치 설계]로 넘긴다.

### Q2. 계산 필드 — 정렬 축인 것만 저장

| 필드 | 저장 여부 | 근거 |
| --- | --- | --- |
| `averageRating` | **저장** | `sortBy=rate` |
| `reviewCount` | **저장** | 평점과 같은 트리거로 함께 갱신 |
| `watcherCount` | **저장** | `sortBy=watcherCount` (Q1) |
| `subscriberCount` | **저장** | `sortBy=subscribeCount` |
| `subscribedByMe` | 파생 | 조회자마다 다르다 — 저장 불가 |
| `hasUnread` | 파생 | 〃 |

**갱신 시점은 원본 트랜잭션 안에서 동기.** 같은 콘텐츠에 리뷰가 동시에 몰리는 상황이 이 프로젝트에서 현실적이지 않고, 어긋난 평점은 눈에 띈다.

### Q3. 계약에 없는 컬럼과 계약의 결함

**내부 전용 컬럼** — `ContentDto`·`ReviewDto`에 `createdAt`이 없는데 둘 다 `sortBy=createdAt`을 요구한다. 저장하되 응답에 넣지 않는다. `PlaylistDto`는 `updatedAt`만 노출한다.

**계약 결함 3건은 글자 그대로 따른다.** 프론트가 그 이름으로 읽으므로 고치면 화면이 깨진다.

| 결함 | 내용 |
| --- | --- |
| `ConversationDto.lastestMessage` | 오타 (`latest`) |
| `UserDto` required에 `isLocked` | 실제 프로퍼티는 `locked` — 존재하지 않는 필드를 required로 선언 |
| `PlaylistDto.subscriberCount` vs `sortBy=subscribeCount` | 이름 불일치 |

**단, 엔티티와 내부 코드에서는 올바른 이름을 쓰고 DTO 경계에서만 계약 이름으로 매핑한다.** 오타를 도메인 전체로 번지게 하지 않는다. `contract/README.md`에 기록했다.

### Q4. 콘텐츠 통합 모델과 태그

**타입별 고유 속성은 부속 테이블.** nullable 컬럼 나열은 "영화인데 왜 점수 컬럼이 있나"를 영원히 설명해야 하고, JSON 컬럼은 **`tmdbId`에 unique 제약을 걸 수 없다** — 배치 재실행 중복 방지가 그 제약에 걸려 있으므로 치명적이다.

**태그는 별도 테이블 + 조인 테이블.** PostgreSQL 배열이 편해 보이나 `tagsIn` 필터와 JPA의 궁합이 나쁘고, 태그 정규화(대소문자·공백)와 목록 조회의 여지가 사라진다.

### Q5. 애그리거트 경계 — 8개 유지, `auth`는 비워 둔다

`conversation`/`directmessage` 분리는 계약이 뒷받침한다(모든 DM 경로가 `conversations/{id}/` 아래). `watchingsession` 독립도 근거가 있다(쓰기가 WebSocket, 저장소가 다름).

**`auth`는 엔티티 없는 패키지로 둔다.** 계약상 인증이 소유한 고유 엔티티가 보이지 않는다 — `UserDto`가 `role`·`locked`를 갖고 `JwtDto`는 그것을 감쌀 뿐이다. 리프레시 토큰과 임시 비밀번호가 후보이나 [인증·인가와 토큰 무효화 설계]에서 정해진다. **지금 빈 `entity`/`repository`를 채우려 들면 없어도 될 테이블이 생긴다.**

### Q6. 삭제 정책 — 물리 삭제, 콘텐츠만 예외 검토

팔로우·구독·플레이리스트-콘텐츠 연결·알림은 관계이거나 소모품이므로 물리 삭제.

**콘텐츠 삭제는 두 문제를 남긴다** — 리뷰·플레이리스트 항목·시청 세션이 딸려 있고, **배치가 같은 콘텐츠를 다시 수집하면 되살아난다.** 후자는 배치 설계의 문제이므로 [콘텐츠 수집 배치 설계]로 넘긴다.

**사용자 탈퇴 경로가 계약에 없다** (`DELETE /api/users/{userId}` 부재). 이번 범위 밖으로 보이나 확정하지 않았다 — 아래 열린 항목 참조.

### unique 제약

- `follows(follower_id, followee_id)` — 중복 팔로우 불가
- `playlist_subscriptions(playlist_id, subscriber_id)` — 중복 구독 불가
- `playlist_contents(playlist_id, content_id)` — 같은 콘텐츠 중복 추가 불가
- 콘텐츠의 외부 ID — `tmdbId`+`mediaType`, `idEvent`. 배치 재실행 중복 방지의 핵심
- **`reviews(author_id, content_id)`** — 사용자당 콘텐츠당 리뷰 1개

마지막 항목은 **계약이 강제하지 않는다.** `POST /api/reviews`와 `PATCH /api/reviews/{id}`가 따로 있을 뿐 "내 리뷰 조회" 경로가 없어 판단 근거가 약하다. 평점 평균이 한 사람의 반복 투표로 왜곡되지 않아야 한다는 이유로 제약을 걸기로 했으나, **이 항목만은 사용자가 아니라 내가 정한 것**이므로 뒤집을 여지를 남긴다.

### 열린 항목

- **사용자 탈퇴를 구현할 것인가** — 계약에 경로가 없다. 없다면 탈퇴한 사용자의 리뷰·플레이리스트 처리라는 문제 자체가 사라진다.


## 보충 (2026-09-03, [실시간 WebSocket 아키텍처 설계] 해결 중)

**`watcher_count`만 갱신 방식이 다르다.** 위 Q2에서 "계산 필드는 원본 트랜잭션 안에서 동기 갱신"으로 정했으나, `contents.watcher_count`는 **예외로 주기적 비동기 동기화**한다.

이유는 갱신 빈도다. 평점·구독자 수는 사용자의 명시적 행동이라 드물지만, 시청 세션 JOIN/LEAVE는 **페이지를 드나들 때마다** 발생해 인기 콘텐츠일수록 같은 행에 쓰기가 몰린다. 브로드캐스트되는 값은 Redis에서 즉시 정확하게 나오므로, 목록에 실리는 숫자가 몇 초 늦는 것은 무해하다.

## 보충 2 (2026-09-03, [인증·인가와 토큰 무효화 설계] 해결 중)

**`auth` 패키지에 저장할 것이 생겼다.** Q5에서 "실제로 생기면 그때 채운다"고 미뤄둔 판정이 났다.

- **리프레시 토큰** — 저장하고 회전한다. 강제 로그아웃과 동시 로그인 제어가 여기에 걸린다.
- **소셜 계정 연결** — `(provider, providerId) → userId`.

**사용자 탈퇴는 구현하지 않는다.** 계약에 `DELETE /api/users/{userId}`가 없다. Q6의 열린 항목 "탈퇴한 사용자의 리뷰·플레이리스트 처리"는 **문제 자체가 사라졌다.** 접근 차단은 `locked`가 담당한다.

**사용자 행에 컬럼이 추가된다** — 임시 비밀번호 해시와 만료 시각.
