package com.extremis.hub.repository;

import com.extremis.hub.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByGoogleSubjectId(String googleSubjectId);
    Optional<User> findByEmail(String email);
}
