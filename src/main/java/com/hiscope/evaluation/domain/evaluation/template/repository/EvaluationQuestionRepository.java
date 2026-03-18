package com.hiscope.evaluation.domain.evaluation.template.repository;

import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationQuestionRepository extends JpaRepository<EvaluationQuestion, Long> {

    List<EvaluationQuestion> findByTemplateIdAndActiveOrderBySortOrderAsc(Long templateId, boolean active);
    List<EvaluationQuestion> findByTemplateIdAndActiveAndQuestionGroupCodeOrderBySortOrderAsc(Long templateId,
                                                                                               boolean active,
                                                                                               String questionGroupCode);

    List<EvaluationQuestion> findByTemplateIdOrderBySortOrderAsc(Long templateId);

    Optional<EvaluationQuestion> findByOrganizationIdAndId(Long organizationId, Long id);

    void deleteByTemplateId(Long templateId);
}
