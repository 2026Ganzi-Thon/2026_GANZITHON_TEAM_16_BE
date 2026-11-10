package com.mowoo.mowoo_backend.login.repository;

import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, UUID> {
    Optional<UserAuthIdentity> findByProviderAndProviderSubject(Provider provider, byte[] providerSubject);
}
