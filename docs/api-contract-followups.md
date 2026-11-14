# 캐릭터·기록 관리 응답 및 삭제 한도

PR #13·#14 리뷰 의견을 반영한 API 계약입니다. 성공 시 모두 HTTP 200을 반환하며 응답 최상위에 `isSuccess`, `code`, `message`를 담습니다.

| API | 성공 code | message | Notion 명세 |
|---|---|---|---|
| GET /api/v1/characters | CHARACTER_LIST_READ_SUCCESS | 캐릭터 목록 조회가 완료됐어요. | [캐릭터 목록](https://app.notion.com/p/411a0460c2bd811ea75cdc7a98a4df6b) |
| GET /api/v1/characters/{characterId} | CHARACTER_READ_SUCCESS | 캐릭터 조회가 완료됐어요. | [캐릭터 상세](https://app.notion.com/p/411a0460c2bd81d1aaf5fb13ffe6cf2d) |
| POST /api/v1/diaries/bulk-delete | DIARIES_DELETE_SUCCESS | 선택한 일기를 삭제했어요. | [선택 일기 삭제](https://app.notion.com/p/411a0460c2bd81849c3ee3cef5517104) |
| DELETE /api/v1/me/records | RECORDS_DELETE_SUCCESS | 전체 기록을 삭제했어요. | [전체 기록 삭제](https://app.notion.com/p/411a0460c2bd8107a26cdd1d93bca61f) |
| DELETE /api/v1/auth/withdraw | ACCOUNT_DELETE_SUCCESS | 회원 탈퇴가 완료됐어요. | [계정 삭제](https://app.notion.com/p/411a0460c2bd8147b609c6ffd01bdf0b) |

## 캐릭터 코드

상세 응답의 `code`는 공통 성공 코드이고, 캐릭터 식별 코드는 `characterCode`입니다. 목록의 `items` 안에서는 캐릭터 식별 코드로 `code`를 사용합니다.

```json
{
  "isSuccess": true,
  "code": "CHARACTER_READ_SUCCESS",
  "message": "캐릭터 조회가 완료됐어요.",
  "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0011",
  "characterCode": "KIND",
  "name": "다정한 모우",
  "description": null,
  "imageUrl": null,
  "isPremium": false,
  "isOwned": true
}
```

## 선택 삭제

`diaryIds`는 1~100개입니다. 중복 제거 전 요청 배열 길이로 제한하며 초과 시 `400 VALIDATION_ERROR`입니다. 중복 ID는 한 번만 삭제합니다. 없는 일기나 다른 사용자의 일기가 하나라도 포함되면 `404 DIARY_NOT_FOUND`로 전체 요청을 거부합니다.

선택 삭제는 일기와 해당 감정·참조를 지우고 대화 메시지는 남깁니다. 전체 기록 삭제는 초안을 포함한 일기·대화를 정리하고 계정·캐릭터 보유·로그인을 유지합니다. 탈퇴는 기록을 정리한 뒤 계정과 인증·보유 정보를 지우고 갱신 쿠키를 만료시킵니다.

DB 제약으로 탈퇴할 수 없으면 `409 ACCOUNT_DELETION_BLOCKED`를 반환하고 해당 요청의 삭제 작업은 롤백됩니다. 추가한 탈퇴 진단 로그에는 제약 이름·SQLState·DB 오류 번호만 기록하며 SQL 원문이나 사용자 데이터는 포함하지 않습니다.
