# GitHub 저장소 개설

Type: task
Status: open

## Question

현재 저장소는 **커밋이 하나도 없고 리모트도 없다**. CI/CD를 결정하려면 실물 저장소가 있어야 한다.

- GitHub 저장소를 만들고 초기 커밋을 올린다(현재 스테이징된 스켈레톤).
- **public인가 private인가** — 커버리지 배지를 README에 다는 요구사항이 있는데, 배지를 만드는 방식에 따라 public 여부가 영향을 준다.
- **`.gitignore` 점검** — 프론트엔드 zip 2개(`project-mopl-fe-1.0.5.zip`, `-release.zip`)가 저장소 루트에 그대로 있다. 압축 해제본과 로컬 설정까지 포함해 무엇을 커밋하고 무엇을 걸러낼지 정한다.

이 티켓은 브랜치 전략·CI/CD 결정을 풀기 위해 존재한다.