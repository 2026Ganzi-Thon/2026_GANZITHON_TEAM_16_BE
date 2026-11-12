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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

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
            throw new BusinessException(AuthErrorCode.ACCOUNT_DELETION_BLOCKED);
        }
    }

    private User requireUserForUpdate(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private void deleteUserRecords(UUID userId) {
        // 사용자 → 세션 → 일기 순서로 잠가 진행 중인 대화·AI 초안 저장과 삭제를 직렬화한다.
        sessions.findAllForUserForUpdate(userId);
        diaries.deleteAllForUser(userId);
        chatService.deleteAllSessions(userId);
    }
}
