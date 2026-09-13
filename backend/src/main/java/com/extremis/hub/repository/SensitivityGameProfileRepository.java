package com.extremis.hub.repository;

import com.extremis.hub.domain.SensitivityGameProfile;
import com.extremis.hub.domain.SupportedGame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensitivityGameProfileRepository
        extends JpaRepository<SensitivityGameProfile, SupportedGame> {
}
