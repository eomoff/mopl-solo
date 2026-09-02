# API 계약

이 서버가 만족시켜야 하는 **외부 계약**이다. 설계 대상이 아니라 따라야 할 제약이며, 문서와 어긋나면 **계약이 이긴다**.

| 파일 | 출처 | 내용 |
| --- | --- | --- |
| `api.json` | `project-mopl-fe-1.0.5/api.json` | OpenAPI 3.1.0 — "모두의 플리 API 문서 1.0" |
| `api.ts` | `project-mopl-fe-1.0.5/src/lib/types/api.ts` | 계약에서 생성된 TypeScript 타입 |

두 파일은 제공된 프론트엔드 번들 **1.0.5**의 사본이다. 원본 번들은 `frontend/`에 있으며 git으로 추적하지 않는다. 번들이 고정 릴리스이므로 사본이 원본과 어긋날 일은 없다.

## 규모

경로 32개 / 오퍼레이션 45개 / 스키마 37개.

## 계약에 **없는** 것

- **소셜 로그인 엔드포인트** — `vite.config.ts`가 `/oauth2`를 백엔드로 프록시하지만 OpenAPI 문서에는 해당 경로가 없다. Spring Security OAuth2 기본 경로를 그대로 쓴다는 뜻으로 읽히며, 확정은 인증 티켓에서 한다.
- **WebSocket / SSE 페이로드** — `/api/sse`는 경로로만 존재한다. 실시간 메시지 형태는 `docs/info.md`의 mermaid 명세가 유일한 출처다.
- **날짜 필드** — `ContentDto`에 개봉일·경기 시작 시각을 담을 자리가 없다.

## 알려진 계약 결함

계약을 **글자 그대로 따른다.** 프론트엔드가 이 이름으로 읽으므로 고치면 화면이 깨진다. 다만 엔티티와 내부 코드에서는 올바른 이름을 쓰고 **DTO 경계에서만 계약 이름으로 매핑한다** — 오타를 도메인 전체로 번지게 하지 않는다.

| 위치 | 결함 |
| --- | --- |
| `ConversationDto.lastestMessage` | 오타. 올바른 철자는 `latest`. |
| `UserDto`의 `required` 목록 | 존재하지 않는 필드 `isLocked`를 required로 선언한다. 실제 프로퍼티는 `locked`. |
| `PlaylistDto` / `GET /api/playlists` | DTO는 `subscriberCount`, 정렬 파라미터는 `subscribeCount`. 이름이 다르다. |

## 계약에 없지만 저장해야 하는 것

정렬 축으로 요구되면서 응답 스키마에는 없는 필드가 있다.

- `ContentDto`에 `createdAt`이 없으나 `GET /api/contents`는 `sortBy=createdAt`을 받는다.
- `ReviewDto`도 마찬가지다 (`sortBy=createdAt`).
- `PlaylistDto`는 `updatedAt`만 노출한다.
