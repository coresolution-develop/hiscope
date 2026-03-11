package com.hiscope.evaluation.domain.evaluation.template.repository;

import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationTemplateRepository extends JpaRepository<EvaluationTemplate, Long> {

    List<EvaluationTemplate> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<EvaluationTemplate> findByOrganizationIdAndActiveOrderByCreatedAtDesc(Long organizationId, boolean active);

    Optional<EvaluationTemplate> findByOrganizationIdAndId(Long organizationId, Long id);
}
