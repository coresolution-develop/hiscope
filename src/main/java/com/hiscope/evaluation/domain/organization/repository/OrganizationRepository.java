package com.hiscope.evaluation.domain.organization.repository;

import com.hiscope.evaluation.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    boolean existsByCode(String code);

    Optional<Organization> findByCode(String code);

    List<Organization> findAllByOrderByCreatedAtDesc();

    List<Organization> findByStatusOrderByCreatedAtDesc(String status);
}
