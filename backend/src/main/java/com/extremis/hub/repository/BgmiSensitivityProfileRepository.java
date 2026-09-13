package com.extremis.hub.repository;

import com.extremis.hub.domain.BgmiSensitivityProfile;
import com.extremis.hub.domain.BgmiSensitivityProfileId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BgmiSensitivityProfileRepository
        extends JpaRepository<BgmiSensitivityProfile, BgmiSensitivityProfileId> {
}
