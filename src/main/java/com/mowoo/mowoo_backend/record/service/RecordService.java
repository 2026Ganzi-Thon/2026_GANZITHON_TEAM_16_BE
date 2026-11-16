package com.mowoo.mowoo_backend.record.service;

import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.chat.service.ChatService;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.login.exception.AuthErrorCode;
import com.mowoo.mowoo_backend.user.entity.User;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.sql.SQLException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecordService {
    private final UserRepository users;
    private final DiaryRepository diaries;
    private final ChatSessionRepository sessions;
    private final ChatService chatService;

    public void deleteDiaries(UUID userId, List<UUID> diaryIds) {
        requireUserForUpdate(userId);
        var ids = new TreeSet<>(diaryIds);
        var owned = diaries.findOwnedForDelete(userId, ids);
        if (owned.size() != ids.size()) {
            throw new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND);
        }
        diaries.deleteOwnedByIds(userId, ids);
    }

    public void deleteRecords(UUID userId) {
        requireUserForUpdate(userId);
        deleteUserRecords(userId);
    }

    public void withdraw(UUID userId) {
        User user = requireUserForUpdate(userId);
        deleteUserRecords(userId);
        try {
            users.delete(user);
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            logDeletionFailure(exception);
            throw new BusinessException(AuthErrorCode.ACCOUNT_DELETION_BLOCKED);
        }
    }

    private User requireUserForUpdate(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private void logDeletionFailure(DataIntegrityViolationException exception) {
        String constraint = "unknown";
        String sqlState = "unknown";
        int vendorCode = 0;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && name.matches("[A-Za-z0-9_.-]{1,128}")) constraint = name;
            }
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (state != null && state.matches("[A-Z0-9]{5}")) sqlState = state;
                vendorCode = sqlException.getErrorCode();
            }
        }
        // SQL 원문·바인딩 값·토큰·일기 본문 대신 제약 이름과 DB 오류 식별자만 남긴다.
        log.warn("회원 탈퇴 차단: constraint={}, sqlState={}, vendorCode={}", constraint, sqlState, vendorCode);
    }

    private void deleteUserRecords(UUID userId) {
        // 사용자 → 세션 → 일기 순서로 잠가 진행 중인 대화·AI 초안 저장과 삭제를 직렬화한다.
        sessions.findAllForUserForUpdate(userId);
        diaries.deleteAllForUser(userId);
        chatService.deleteAllSessions(userId);
    }
}
