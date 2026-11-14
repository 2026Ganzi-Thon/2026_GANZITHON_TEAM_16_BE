package com.mowoo.mowoo_backend.login.client;

import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;

public record SocialUser(Provider provider, String subject, String nickname, String email, String profileImageUrl) {
}
