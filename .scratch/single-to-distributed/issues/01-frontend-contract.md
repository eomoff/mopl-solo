# 프론트엔드 계약 확보와 1.0.5 차이 확인

Type: task
Status: resolved

## Question

저장소 루트의 `project-mopl-fe-1.0.5.zip`을 풀어 API 계약을 저장소 안에서 참조 가능한 형태로 확보한다.

- `api.json`(OpenAPI, 60K)과 `src/lib/types/api.ts`(112K)를 **어디에 둘 것인가** — 압축 해제본을 커밋할지, 추출한 요약만 커밋할지. `.gitignore` 정책 포함.
- **스키마와 경로의 전체 목록** — 몇 개이고 어떤 리소스로 묶이는가. 이후 도메인 모델 티켓이 이 목록 위에서 움직인다.
- `docs/info.md` 본문은 `1.0.4` 기준으로 쓰여 있는데 실물은 **`1.0.5`** 다. **차이가 있는가**, 있다면 무엇인가. 문서와 계약이 어긋나면 계약이 이긴다.
- `-release.zip`의 `dist/`를 개발 중 어떻게 서빙할 것인가 — Spring 정적 리소스로 태울지, 별도로 띄우고 CORS를 열지.

이 티켓이 끝나야 도메인 모델·인증·실시간 티켓이 추측이 아니게 된다.

## Answer

### 배치 결정

| 위치 | git | 내용 | 근거 |
| --- | --- | --- | --- |
| `contract/` | **커밋** | `api.json`(OpenAPI 3.1.0), `api.ts`(생성된 타입), `README.md` | 지도가 이것을 소스 오브 트루스로 선언했으므로 저장소 안에 있어야 한다. 172KB로 비용이 없다. |
| `frontend/` | **미추적** | 제공 번들 원본(`project-mopl-fe-1.0.5/`)과 zip 2개 | 205개 파일의 서드파티 node 프로젝트를 Java 저장소 이력에 넣을 이유가 없다. 고정 릴리스라 사본과 어긋나지 않는다. |
| `src/main/resources/static/` | **커밋** | `dist/`의 내용(`index.html`, `favicon.svg`, `assets/`) | 아래 서빙 결정 참조. |

`.gitignore`에 `/frontend/`와 `.DS_Store`를 추가했다.

### 서빙 결정 — Spring 정적 리소스로 태운다. CORS 설정 불필요.

두 가지 사실이 이 결정을 강제했다.

1. **`src/lib/api/client.ts`가 `baseURL: import.meta.env.VITE_API_BASE_URL || ''`** — 기본값이 빈 문자열이다. 즉 빌드된 프론트는 `/api/...`를 **같은 오리진 상대 경로**로 호출한다. Spring이 서빙하면 CORS 문제 자체가 발생하지 않는다.
2. **HashRouter를 쓴다**(`CLAUDE.md` 명시, `info.md`의 `#/sign-in` 예시와 일치). 모든 라우트가 `#` 뒤에 있으므로 서버는 언제나 `/index.html`만 주면 된다. **SPA 폴백 포워딩 컨트롤러가 필요 없다.**

부수 효과: Spring Security에서 정적 리소스를 `permitAll`로 열어야 한다 → [인증·인가와 토큰 무효화 설계]로 넘긴다.

개발 중 대안도 열려 있다 — `vite.config.ts`에 `/api`, `/oauth2`, `/ws`(웹소켓 포함)를 `localhost:8080`으로 보내는 프록시가 이미 설정되어 있어, `pnpm dev`로 띄워도 CORS 없이 붙는다. 빌드 산출물은 정적 검증용, dev 서버는 프론트를 수정해볼 때 쓴다.

### 계약의 규모와 형태

**경로 32개 / 오퍼레이션 45개 / 스키마 37개.** OpenAPI 3.1.0, 제목 "모두의 플리 API 문서 1.0".

리소스별 경로 수: `users` 6, `conversations` 5, `auth` 5, `playlists` 4, `follows` 4, `contents` 3, `reviews` 2, `notifications` 2, `sse` 1.

도메인 패키지 배치에 영향을 주는 관찰:

- **`watchingsession`에는 쓰기 경로가 없다.** REST는 읽기 2개뿐(`/api/contents/{contentId}/watching-sessions`, `/api/users/{watcherId}/watching-sessions`). **세션의 생성·소멸은 전적으로 WebSocket이 담당한다.**
- **`directmessage`도 독립 경로가 없다.** 전부 `/api/conversations/{id}/direct-messages` 아래에 있다 — 계약이 `conversation`을 애그리거트 루트로 취급한다.
- **어드민 전용 경로가 따로 없다.** 권한 변경·잠금이 `/api/users/{userId}/role`, `/api/users/{userId}/locked`로 일반 사용자 경로에 섞여 있다 → 인가는 경로가 아니라 **메서드 수준**에서 갈린다.
- **`/api/conversations/with`** — 상대방을 지정해 대화를 찾는 경로가 따로 있다. 1:1 대화의 유일성 보장이 필요하다는 신호.
- 커서 페이지네이션(`CursorResponse*`)이 목록 응답의 기본형이다.
- **소셜 로그인 경로가 계약에 없다.** `vite.config.ts`는 `/oauth2`를 백엔드로 프록시하는데 OpenAPI에는 없다 → Spring Security OAuth2 기본 경로를 그대로 쓴다는 뜻으로 읽히며, 확정은 [인증·인가와 토큰 무효화 설계]에서.
- **WebSocket/SSE 페이로드는 계약 밖이다.** `/api/sse`는 경로로만 존재한다. 실시간 메시지 형태의 유일한 출처는 `docs/info.md`의 mermaid 명세다.

### 하지 않은 것

**1.0.4 ↔ 1.0.5 차이 확인은 사용자 지시로 생략했다.** 1.0.5를 그대로 쓴다. `docs/info.md` 본문이 1.0.4를 언급하는 부분과 어긋나면 계약이 이긴다.
