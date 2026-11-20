package com.mowoo.mowoo_backend.user.service;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.user.dto.request.UpdateUserProfileRequest;
import com.mowoo.mowoo_backend.user.dto.response.UserProfileResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import com.mowoo.mowoo_backend.user.exception.UserErrorCode;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> UserProfileResponse.from(user, CommonSuccessCode.USER_PROFILE_READ_SUCCESS))
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public UserProfileResponse updateUserProfile(UUID userId, UpdateUserProfileRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        user.updateProfile(
                request.getNickname() == null ? user.getNickname() : request.getNickname().strip(),
                request.isEmailSpecified() ? request.getEmail() : user.getEmail(),
                request.isBirthDateSpecified() ? request.getBirthDate() : user.getBirthDate(),
                request.getUsePastDiaries() == null ? user.isUsePastDiaries() : request.getUsePastDiaries(),
                request.getEveningReminderEnabled() == null
                        ? user.isEveningReminderEnabled() : request.getEveningReminderEnabled()
        );
        return UserProfileResponse.from(user, CommonSuccessCode.USER_PROFILE_UPDATE_SUCCESS);
    }
}
