package com.hiscope.evaluation.domain.settings.repository;

import com.hiscope.evaluation.domain.settings.entity.OrganizationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationSettingRepository extends JpaRepository<OrganizationSetting, Long> {

    Optional<OrganizationSetting> findByOrganizationIdAndSettingKey(Long organizationId, String settingKey);

    List<OrganizationSetting> findByOrganizationId(Long organizationId);
}
