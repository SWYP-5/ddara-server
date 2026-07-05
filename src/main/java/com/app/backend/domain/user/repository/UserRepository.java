package com.app.backend.domain.user.repository;

import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    // 탈퇴 후 보존기간이 지나 완전 삭제할 대상 (deleted_at < cutoff)
    List<User> findByDeletedAtBefore(LocalDateTime cutoff);
}
