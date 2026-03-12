package com.hiscope.evaluation.domain.evaluation.rule.repository;

import com.hiscope.evaluation.domain.evaluation.rule.entity.RelationshipDefinitionSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipDefinitionSetRepository extends JpaRepository<RelationshipDefinitionSet, Long> {

    List<RelationshipDefinitionSet> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<RelationshipDefinitionSet> findByOrganizationIdAndId(Long organizationId, Long id);

    Optional<RelationshipDefinitionSet> findByOrganizationIdAndIsDefaultTrueAndActiveTrue(Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    Optional<RelationshipDefinitionSet> findByIdAndActiveTrue(Long id);
}
