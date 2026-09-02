# 도메인 모델 확정

Type: grilling
Status: open
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
