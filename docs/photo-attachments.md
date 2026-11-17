# 사진 첨부 사용 방법

별도 업로드 API 없이 기존 세 API에서 `application/json`과 `multipart/form-data`를 모두 지원한다. 인증은 기존과 같은 `Authorization: Bearer {accessToken}`이다.

| 기능 | API | 사진이 있는 요청 |
|---|---|---|
| 일기 작성 | `POST /api/v1/diaries` | `payload`에 작성 JSON 문자열, `files`에 반복 사진 |
| 일기 수정 | `PATCH /api/v1/diaries/{diaryId}` | `payload`에 수정 JSON 문자열, `files`에 추가 사진 |
| 메시지 전송 | `POST /api/v1/chat-sessions/{sessionId}/messages` | `payload`에 전송 JSON 문자열, `files`에 반복 사진 |

사진이 없는 기존 JSON 요청과 성공 상태 코드·성공 코드는 유지한다. 일기 수정 JSON에서도 `removeAttachmentIds`로 사진을 지울 수 있다. 이 필드는 생략 시 유지, 빈 배열이면 삭제 없음이며 명시적 null은 400이다. 메시지 수정은 기존 사진을 유지한다.

## 프론트 요청 예시

```javascript
const form = new FormData();
form.append('payload', JSON.stringify({
  entryDate: '2026-11-17',
  title: '오늘의 사진',
  content: '오늘 있었던 일을 기록했다.'
}));
for (const photo of selectedPhotos) form.append('files', photo);

await fetch(`${baseUrl}/api/v1/diaries`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${accessToken}` },
  body: form
});
```

FormData 사용 시 `Content-Type`은 직접 지정하지 않는다. 브라우저가 multipart boundary를 포함해 설정한다. `payload`는 JSON 문자열이므로 별도 JSON Blob 변환이 필요 없다.

일기 사진 교체는 다음 JSON 문자열을 `payload`에 넣고 새 사진들을 `files`로 보낸다. 삭제 후 남는 사진과 추가 사진의 합계를 검증하므로 3장을 보유한 일기도 한 요청에서 교체할 수 있다.

```json
{
  "title": "수정한 제목",
  "removeAttachmentIds": ["삭제할-첨부-UUID"]
}
```

메시지는 `{"content":"오늘 사진이야","characterId":"캐릭터-UUID"}`를 보낸다. `characterId`는 기존처럼 선택이다. 사진만 보낼 때는 `payload`를 `{}`로 보낼 수 있다. 본문과 사진이 모두 없으면 기존 `MESSAGE_EMPTY`다.

## 응답과 제한

일기 작성·수정·상세·목록·감정 확정 응답과 메시지 전송·수정·대화 상세 응답의 각 메시지에 `attachments` 배열이 포함된다. AI 메시지는 현재 사진을 생성하지 않으므로 빈 배열이다.

```json
{
  "attachments": [
    {
      "id": "첨부-UUID",
      "mimeType": "image/png",
      "url": "https://비공개-S3-객체의-서명된-주소",
      "sortOrder": 0
    }
  ]
}
```

- JPEG와 PNG만 허용한다. 파일명이나 확장자를 신뢰하지 않고 MIME·시그니처·실제 이미지 디코딩을 검증한다. SVG·GIF·WebP·HEIC는 이번 구현에서 지원하지 않는다.
- 일기/사용자 메시지당 3장, 파일당 10MiB까지다. 요청 전체 제한은 기존 Spring/Nginx의 30MB이며 JSON과 multipart 헤더도 포함한다.
- 디코딩 전 크기를 읽어 최대 2천만 픽셀 및 가로/세로 10,000px로 제한한다.
- S3 키는 서버가 UUID로 생성한다. 원본 파일명이나 사용자 제공 경로는 키에 반영하지 않는다.
- 소유권 확인 후 5분간 유효한 S3 GET 서명 URL을 생성한다. URL이 만료되면 기존 상세/목록 API를 다시 조회한다. URL 자체를 받은 사람은 만료 전까지 사용할 수 있으므로 외부에 공유하지 않는다.
- 응답 URL·파일 내용·사용자 파일 이름을 로그에 남기지 않는다. 삭제 재시도 로그는 작업 ID와 예외 종류만 남긴다.
- **이미지 분석은 포함하지 않는다.** 사진과 접근 URL은 Upstage에 보내지 않는다. 사진만 있는 메시지의 AI 입력에는 사진을 첨부했다는 설명과 내용을 추측하지 말라는 문구를 보낸다. 기존 텍스트 AI 대화·초안 생성 흐름은 유지한다.

| 실패 | HTTP | code |
|---|---|---|
| 잘못된 payload/필수값 | 400 | `VALIDATION_ERROR` |
| 빈 파일·지원하지 않는 형식·손상된 이미지·과도한 픽셀 수 | 400 | `INVALID_IMAGE` |
| 사진 개수 초과 | 400 | `TOO_MANY_ATTACHMENTS` |
| 파일/요청 용량 초과 | 413 | `PAYLOAD_TOO_LARGE` |
| 해당 일기에 없거나 다른 사용자의 삭제 대상 사진 | 404 | `ATTACHMENT_NOT_FOUND` |
| S3 비활성·업로드/서명 실패 | 503 | `STORAGE_UNAVAILABLE` |

## 삭제와 오류 처리

- 새 객체는 업로드 전에 DB에 독립적인 정리 예약을 만든다. AI 생성·DB 저장·응답 URL 생성이 실패해 요청이 롤백되면 업로드한 객체를 정리한다.
- 성공하면 정리 예약만 없앤다. 프로세스가 중간에 종료돼 예약이 남아도 재시도 시 실제 첨부 행이 존재하면 사진을 지우지 않는다. 미연결 객체의 예약은 24시간 뒤 실행 대상이 된다.
- 기존 사진 삭제는 DB 트랜잭션이 커밋된 후 실행한다. 트랜잭션이 롤백되면 기존 사진은 유지한다.
- S3 삭제 실패는 `file_deletion_jobs`에 남아 5분 간격으로 재시도한다. 계정이 삭제돼도 작업은 남는다. 삭제는 멱등이며 실패 작업은 다음 실행 시각을 늦춰 뒤 작업도 처리되게 한다.
- 선택 일기 삭제는 해당 일기의 사진만, 대화 삭제는 해당 대화의 메시지 사진만 지운다. 전체 기록 삭제·회원 탈퇴는 양쪽 사진을 모두 정리한다. 대화 삭제로 보존되는 일기의 사진은 남는다.

새 테이블은 `attachments`, `file_deletion_jobs` 두 개다. 현재 프로젝트의 `ddl-auto: update`로 생성한다. Flyway는 추가하지 않았다. `attachments`는 일기/메시지 중 정확히 하나에 속하고, 부모 FK에는 CASCADE가 있으나 실제 객체 정리는 서비스가 삭제 작업을 등록해서 수행한다. 운영에서 SQL로 부모 행만 직접 지우면 S3 정리가 실행되지 않으므로 서비스 경로를 사용한다.

## 로컬과 운영 설정

로컬에서는 `.env`에 아래 값을 설정한다. 실제 자격 증명은 커밋하지 않는다. `AWS_S3_ENABLED=false`일 때도 사진이 없는 기존 API는 사용할 수 있다.

```properties
AWS_S3_ENABLED=true
AWS_REGION=ap-southeast-2
AWS_S3_BUCKET=비공개-사진-버킷
AWS_S3_LOCATION=uploads
# 로컬에서 필요한 경우에만 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 설정
```

운영은 액세스 키 없이 EC2 인스턴스 역할을 사용한다. 역할에는 해당 버킷 `uploads/*`의 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`만 허용하면 된다.

`infra/environment.yaml`에 `EnablePhotoStorage=true`를 전달하면 공개 접근 차단·ACL 비활성·SSE-S3 암호화·HTTPS 강제 정책을 적용한 전용 버킷과 역할 권한, 환경변수를 구성한다. 기본값은 false이며 템플릿/코드를 작성한 것만으로 AWS 설정이 바뀌지는 않는다. 버킷은 스택 삭제 시 보존한다. 프론트에서 버킷에 직접 업로드하지 않으므로 업로드용 버킷 CORS나 공개 정책은 필요 없다.

## 검증

실제 MySQL 테스트 DB에서 multipart 요청, 소유권, 교체/동시 추가 한도, 사진만 있는 메시지, AI 실패와 부분 업로드 실패 롤백, 커밋 후 삭제 재시도, 프로세스 종료 후 정리 예약 회수, 계정 삭제를 검증한다. 객체 저장소와 유료 AI는 테스트 대역을 사용한다. S3 서명 테스트는 네트워크 없이 실제 SDK로 5분 URL과 응답 헤더를 확인한다. 실제 AWS 업로드나 유료 AI 호출은 별도 운영 연동 검증이 필요하다.

참고: [AWS S3 서명 URL](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html), [AWS 기본 자격 증명 체인](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), [Spring 트랜잭션 동기화](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html).
