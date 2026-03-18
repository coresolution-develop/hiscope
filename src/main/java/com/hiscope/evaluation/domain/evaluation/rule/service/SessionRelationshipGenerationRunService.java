package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.rule.entity.SessionRelationshipGenerationRun;
import com.hiscope.evaluation.domain.evaluation.rule.enums.RelationshipGenerationMode;
import com.hiscope.evaluation.domain.evaluation.rule.repository.SessionRelationshipGenerationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionRelationshipGenerationRunService {

    private final SessionRelationshipGenerationRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public List<SessionRelationshipGenerationRun> findLatestRuns(Long sessionId, int size) {
        return runRepository.findBySessionIdOrderByExecutedAtDesc(sessionId, PageRequest.of(0, Math.max(1, size)));
    }

    public boolean hasSuccessfulRuleBasedRun(Long sessionId) {
        return runRepository.existsBySessionIdAndGenerationModeAndStatus(
                sessionId,
                RelationshipGenerationMode.RULE_BASED,
                "SUCCESS"
        );
    }

    @Transactional
    public void recordSuccess(Long orgId,
                              Long sessionId,
                              RelationshipGenerationMode mode,
                              RelationshipGenerationService.GenerationSummary summary,
                              long overrideAppliedCount,
                              long finalCount) {
        runRepository.save(SessionRelationshipGenerationRun.builder()
                .organizationId(orgId)
                .sessionId(sessionId)
                .generationMode(mode)
                .status("SUCCESS")
                .generatedCount(summary.generatedRelationshipCount())
                .excludedCount(summary.excludedCount())
                .selfRemovedCount(summary.selfExcludedCount())
                .deduplicatedCount(summary.duplicateRemovedCount())
                .overrideAppliedCount(overrideAppliedCount)
                .finalCount(finalCount)
                .ruleStatsJson(toJson(summary.generatedCountByRule()))
                .executedBy(resolveCurrentUserId())
                .executedByLoginId(resolveCurrentUserLoginId())
                .build());
    }

    @Transactional
    public void recordFailure(Long orgId,
                              Long sessionId,
                              RelationshipGenerationMode mode,
                              String errorMessage) {
        runRepository.save(SessionRelationshipGenerationRun.builder()
                .organizationId(orgId)
                .sessionId(sessionId)
                .generationMode(mode)
                .status("FAILED")
                .errorMessage(errorMessage)
                .executedBy(resolveCurrentUserId())
                .executedByLoginId(resolveCurrentUserLoginId())
                .build());
    }

    private String toJson(Map<Long, Long> ruleStats) {
        if (ruleStats == null || ruleStats.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ruleStats);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Long resolveCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUser().getId();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCurrentUserLoginId() {
        try {
            return SecurityUtils.getCurrentUser().getUsername();
        } catch (Exception e) {
            return null;
        }
    }
}
