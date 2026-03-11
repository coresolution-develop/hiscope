package com.hiscope.evaluation.domain.evaluation.response.repository;

import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluationResponseItemRepository extends JpaRepository<EvaluationResponseItem, Long> {

    List<EvaluationResponseItem> findByResponseId(Long responseId);

    List<EvaluationResponseItem> findByResponseIdIn(Collection<Long> responseIds);

    Optional<EvaluationResponseItem> findByResponseIdAndQuestionId(Long responseId, Long questionId);
}
