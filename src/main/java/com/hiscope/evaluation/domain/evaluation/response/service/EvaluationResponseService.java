package com.hiscope.evaluation.domain.evaluation.response.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.dto.EvaluationSubmitRequest;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationResponseService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationResponseItemRepository itemRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationQuestionRepository questionRepository;

    /** 현재 직원의 배정 목록 조회 */
    public List<EvaluationAssignment> findMyAssignments(Long employeeId, Long orgId) {
        return assignmentRepository.findByEvaluatorAndOrg(employeeId, orgId);
    }

    /** 특정 배정의 평가 폼 데이터 조회 */
    public EvaluationAssignment getAssignment(Long employeeId, Long orgId, Long assignmentId) {
        EvaluationAssignment assignment = assignmentRepository.findByOrganizationIdAndId(orgId, assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        // 자신의 배정만 접근 가능
        if (!assignment.getEvaluatorId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인에게 배정된 평가만 접근할 수 있습니다.");
        }
        return assignment;
    }

    public List<EvaluationQuestion> getQuestionsForAssignment(Long orgId, Long assignmentId) {
        EvaluationAssignment assignment = assignmentRepository.findByOrganizationIdAndId(orgId, assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        EvaluationSession session = sessionRepository.findByOrganizationIdAndId(orgId, assignment.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return findQuestionsByAssignment(session.getTemplateId(), assignment.getResolvedQuestionGroupCode());
    }

    public EvaluationResponse findResponse(Long assignmentId) {
        return responseRepository.findByAssignmentId(assignmentId).orElse(null);
    }

    public List<EvaluationResponseItem> findResponseItems(Long responseId) {
        return itemRepository.findByResponseId(responseId);
    }

    /**
     * 평가 저장 (임시저장 또는 최종제출)
     *
     * 처리 순서:
     *   ① 세션 진행 상태 확인
     *   ② 재제출 허용 여부 확인
     *   ③ 제출된 문항 ID가 해당 세션 템플릿 소속인지 검증 (타 템플릿 문항 ID 주입 방지)
     *   ④ SCALE 문항 점수 범위 검증 (1 ~ maxScore)
     *   ⑤ 재제출 시작 시 assignment/response 상태 초기화 (임시저장 시점)
     *   ⑥ 항목 upsert → 최종제출 처리
     */
    @Transactional
    public EvaluationResponse save(Long employeeId, Long orgId, Long assignmentId,
                                   EvaluationSubmitRequest request) {
        EvaluationAssignment assignment = getAssignment(employeeId, orgId, assignmentId);

        EvaluationSession session = sessionRepository.findByOrganizationIdAndId(orgId, assignment.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.isInProgress()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        if (assignment.isSubmitted() && !session.isAllowResubmit()) {
            throw new BusinessException(ErrorCode.RESUBMIT_NOT_ALLOWED);
        }

        // ③④ 문항 유효성 검증: 세션 템플릿 소속 여부 + SCALE 점수 범위
        List<EvaluationQuestion> questions = findQuestionsByAssignment(
                session.getTemplateId(),
                assignment.getResolvedQuestionGroupCode()
        );
        Map<Long, EvaluationQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(EvaluationQuestion::getId, q -> q));
        validateAnswers(request, questionMap);

        // 기존 응답 조회
        EvaluationResponse response = responseRepository.findByAssignmentId(assignmentId).orElse(null);

        // ⑤ 재제출 시작 — 임시저장 시점에 이전 제출 상태 해제
        //    allowResubmit=true 일 때 최종제출 후 폼에서 버튼이 사라지는 문제 해결
        //    임시저장으로 재편집을 시작할 때 assignment=PENDING, response.finalSubmit=false 로 초기화
        if (assignment.isSubmitted() && session.isAllowResubmit() && !request.isFinalSubmit()) {
            assignment.reopen();
            if (response != null) response.reopen();
        }

        // Response 생성 (없는 경우)
        if (response == null) {
            response = responseRepository.save(EvaluationResponse.builder()
                    .assignmentId(assignmentId)
                    .organizationId(orgId)
                    .finalSubmit(false)
                    .build());
        }

        // ⑥ 항목 upsert
        if (request.getScores() != null) {
            for (Map.Entry<Long, Integer> entry : request.getScores().entrySet()) {
                saveOrUpdateItem(response.getId(), entry.getKey(), entry.getValue(), null);
            }
        }
        if (request.getTexts() != null) {
            for (Map.Entry<Long, String> entry : request.getTexts().entrySet()) {
                saveOrUpdateItem(response.getId(), entry.getKey(), null, entry.getValue());
            }
        }

        // 최종제출 처리
        if (request.isFinalSubmit()) {
            response.finalize();
            assignment.submit();
        }

        return response;
    }

    /**
     * 제출 데이터 사전 검증
     * - questionId 가 현재 세션 템플릿 소속인지 확인 (임의 ID 주입 방지)
     * - SCALE 문항 점수가 허용 범위(1 ~ maxScore) 내인지 확인
     */
    private void validateAnswers(EvaluationSubmitRequest request,
                                 Map<Long, EvaluationQuestion> questionMap) {
        if (request.getScores() != null) {
            for (Map.Entry<Long, Integer> entry : request.getScores().entrySet()) {
                EvaluationQuestion q = questionMap.get(entry.getKey());
                if (q == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "유효하지 않은 문항 ID가 포함되어 있습니다.");
                }
                Integer score = entry.getValue();
                if (score != null && q.getMaxScore() != null
                        && (score < 1 || score > q.getMaxScore())) {
                    String title = q.getContent().length() > 20
                            ? q.getContent().substring(0, 20) + "…" : q.getContent();
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            String.format("'%s' 점수는 1~%d 사이여야 합니다.", title, q.getMaxScore()));
                }
            }
        }
        if (request.getTexts() != null) {
            for (Long qId : request.getTexts().keySet()) {
                if (!questionMap.containsKey(qId)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "유효하지 않은 문항 ID가 포함되어 있습니다.");
                }
            }
        }
    }

    private void saveOrUpdateItem(Long responseId, Long questionId, Integer score, String text) {
        EvaluationResponseItem item = itemRepository.findByResponseIdAndQuestionId(responseId, questionId)
                .orElseGet(() -> EvaluationResponseItem.builder()
                        .responseId(responseId).questionId(questionId).build());
        item.update(score, text);
        itemRepository.save(item);
    }

    private List<EvaluationQuestion> findQuestionsByAssignment(Long templateId, String resolvedQuestionGroupCode) {
        if (resolvedQuestionGroupCode == null || resolvedQuestionGroupCode.isBlank()) {
            return questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(templateId, true);
        }
        List<EvaluationQuestion> groupedQuestions = questionRepository
                .findByTemplateIdAndActiveAndQuestionGroupCodeOrderBySortOrderAsc(templateId, true, resolvedQuestionGroupCode);
        if (groupedQuestions.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "배정된 문항군(" + resolvedQuestionGroupCode + ")에 해당하는 문항이 템플릿에 없습니다."
            );
        }
        return groupedQuestions;
    }
}
