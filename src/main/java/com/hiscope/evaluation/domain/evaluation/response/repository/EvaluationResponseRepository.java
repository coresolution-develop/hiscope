package com.hiscope.evaluation.domain.evaluation.response.repository;

import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvaluationResponseRepository extends JpaRepository<EvaluationResponse, Long> {

    Optional<EvaluationResponse> findByAssignmentId(Long assignmentId);
}
