# 커밋 및 PR 양식

- 커밋 제목은 `<type>: <변경 요약>`으로 작성한다. 타입은 소문자로 쓰고 제목 아래 한 줄을 비운 뒤 변경 내용을 목록으로 적는다.
- PR 제목은 `[Type] <변경 요약>`으로 작성한다. 예를 들어 기능 추가는 `[Feat]`, 설정이나 유지보수 변경은 `[Chore]`를 사용한다.
- PR 본문은 `PR 타입(하나 이상의 PR 타입을 선택해주세요)`, `반영 브랜치`, `변경 사항`, `테스트 결과`, `참고 사항` 순서로 작성한다.
- PR 타입은 기능 추가, 기능 삭제, 버그 수정, 의존성·환경 변수·빌드 관련 코드 업데이트, 리팩터링 중 해당하는 항목을 체크한다. 브랜치는 `head -> base`로 표기하고 변경 사항은 번호와 하위 목록으로 정리한다.
- API가 바뀐 PR에는 주요 API 표와 흐름을 변경 사항 뒤에 추가한다. 테스트 결과에는 실제로 확인한 내용만 적는다.

# 공통 응답·예외·ID 규칙

- 성공 응답은 `isSuccess: true`, `code`, `message`를 DTO 최상위에 담는다. `code`는 `CommonSuccessCode` 상수 이름이고, `message`는 해당 상수의 안내 문구다. 상태 코드는 생성 201, 그 외 200으로 맞춘다. 별도 데이터가 없으면 `SuccessResponse`를 반환한다.
- 페이지가 없는 목록은 `ListResponse<T>`(`items`), 커서 페이지 목록은 `CursorResponse<T>`(`items`, `nextCursor`)로 반환한다.
- 실패는 `throw new BusinessException(<도메인>ErrorCode.<코드>)`로 던진다. 공통 실패 응답에는 `isSuccess: false`, `message`, `error: {code, message}`가 포함된다.
- 성공·실패 코드 enum은 `BaseCode`를 구현한다. enum 상수 이름은 API 명세와 맞춘다. 성공은 최상위 `code`, 실패는 `error.code`에 담는다.
- 엔티티 ID는 `UUID` 타입에 `@GeneratedValue(strategy = GenerationType.UUID)`를 붙인다. FK와 복합키의 UUID도 전역 설정에 따라 36자 문자열로 저장된다.
