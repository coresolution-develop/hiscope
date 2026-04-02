package com.hiscope.evaluation.domain.evaluation.response.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.response.dto.EvaluationSubmitRequest;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.MyEvaluationGroupView;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.MyEvaluationItemView;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.entity.SessionEmployeeSnapshot;
import com.hiscope.evaluation.domain.evaluation.session.repository.SessionEmployeeSnapshotRepository;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
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
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EvaluationRelationshipRepository relationshipRepository;
    private final SessionEmployeeSnapshotRepository snapshotRepository;

    /** 현재 직원의 배정 목록 조회 */
    public List<EvaluationAssignment> findMyAssignments(Long employeeId, Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        return assignmentRepository.findByEvaluatorAndOrg(employeeId, orgId);
    }

    /**
     * 세션 기준으로 그룹핑된 평가 목록 조회
     * - 피평가자명/부서: Employee JOIN
     * - 관계 유형 한글 변환: EvaluationRelationship JOIN
     */
    public List<MyEvaluationGroupView> findMyEvaluationGroups(Long employeeId, Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        List<EvaluationAssignment> assignments = assignmentRepository.findByEvaluatorAndOrg(employeeId, orgId);
        if (assignments.isEmpty()) return List.of();

        // 세션 일괄 조회
        Set<Long> sessionIds = assignments.stream()
                .map(EvaluationAssignment::getSessionId).collect(Collectors.toSet());
        Map<Long, EvaluationSession> sessionMap = sessionRepository.findAllById(sessionIds).stream()
                .filter(s -> "IN_PROGRESS".equals(s.getStatus()))
                .collect(Collectors.toMap(EvaluationSession::getId, s -> s));
        if (sessionMap.isEmpty()) {
            return List.of();
        }

        // 진행중(IN_PROGRESS) 세션에 속한 배정만 노출
        List<EvaluationAssignment> inProgressAssignments = assignments.stream()
                .filter(a -> sessionMap.containsKey(a.getSessionId()))
                .toList();
        if (inProgressAssignments.isEmpty()) {
            return List.of();
        }

        // 직원 IN 쿼리 조회 (피평가자만, 스냅샷 없는 세션의 fallback용)
        Set<Long> evaluateeIds = inProgressAssignments.stream()
                .map(EvaluationAssignment::getEvaluateeId).collect(Collectors.toSet());
        Map<Long, Employee> employeeMap = employeeRepository.findAllById(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // 부서 IN 쿼리 조회 (fallback용)
        Set<Long> deptIds = employeeMap.values().stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of() :
                departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        // 스냅샷 일괄 조회 (세션별 → 직원별 중첩 맵)
        Map<Long, Map<Long, SessionEmployeeSnapshot>> snapshotsBySession = new HashMap<>();
        for (Long sid : sessionMap.keySet()) {
            List<SessionEmployeeSnapshot> snaps = snapshotRepository.findAllBySessionId(sid);
            if (!snaps.isEmpty()) {
                snapshotsBySession.put(sid, snaps.stream()
                        .collect(Collectors.toMap(SessionEmployeeSnapshot::getEmployeeId, s -> s)));
            }
        }

        // 관계 일괄 조회
        Set<Long> relIds = inProgressAssignments.stream()
                .map(EvaluationAssignment::getRelationshipId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> relTypeMap = relIds.isEmpty() ? Map.of() :
                relationshipRepository.findAllById(relIds).stream()
                        .collect(Collectors.toMap(EvaluationRelationship::getId, EvaluationRelationship::getRelationType));

        // 아이템 생성
        List<MyEvaluationItemView> items = inProgressAssignments.stream().map(a -> {
            // 1순위: 스냅샷 (세션 확정 시점 정보), 2순위: employees 원본 fallback
            SessionEmployeeSnapshot snap = snapshotsBySession
                    .getOrDefault(a.getSessionId(), Map.of())
                    .get(a.getEvaluateeId());
            Employee ev = employeeMap.get(a.getEvaluateeId());
            String name = snap != null ? snap.getName()
                    : (ev != null ? ev.getName() : "직원#" + a.getEvaluateeId());
            String dept = snap != null ? snap.getDepartmentName()
                    : (ev != null ? deptNameMap.get(ev.getDepartmentId()) : null);
            String rel = translateRelationType(
                    a.getRelationshipId() != null ? relTypeMap.get(a.getRelationshipId()) : null);
            return new MyEvaluationItemView(
                    a.getId(), a.getEvaluateeId(), name, dept, rel,
                    a.getStatus(), a.getSubmittedAt(), a.getSessionId());
        }).toList();

        // 세션별 그룹핑 → 세션 시작일 내림차순 정렬
        return items.stream()
                .collect(Collectors.groupingBy(MyEvaluationItemView::sessionId,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    EvaluationSession s = sessionMap.get(e.getKey());
                    return new MyEvaluationGroupView(
                            e.getKey(),
                            s != null ? s.getName() : "세션#" + e.getKey(),
                            s != null ? s.getStartDate() : null,
                            s != null ? s.getEndDate() : null,
                            e.getValue());
                })
                .sorted(Comparator.comparing(
                        g -> g.startDate() != null ? g.startDate() : LocalDate.MIN,
                        Comparator.reverseOrder()))
                .toList();
    }

    private String translateRelationType(String rawType) {
        if (rawType == null) return "기타";
        return switch (rawType) {
            case "UPWARD"     -> "상향 평가";
            case "DOWNWARD"   -> "하향 평가";
            case "PEER"       -> "동료 평가";
            case "CROSS_DEPT" -> "타부서 평가";
            case "MANUAL"     -> "직접 지정";
            default           -> "기타";
        };
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
        if (request.isFinalSubmit()) {
            validateRequiredAnswersOnFinalSubmit(questions, request);
        }

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

    private void validateRequiredAnswersOnFinalSubmit(List<EvaluationQuestion> questions,
                                                      EvaluationSubmitRequest request) {
        List<EvaluationQuestion> orderedQuestions = questions.stream()
                .sorted(Comparator.comparingInt(EvaluationQuestion::getSortOrder)
                        .thenComparing(EvaluationQuestion::getId))
                .toList();

        if (orderedQuestions.isEmpty()) {
            return;
        }

        Map<Long, Integer> numberByQuestionId = new HashMap<>();
        for (int i = 0; i < orderedQuestions.size(); i++) {
            numberByQuestionId.put(orderedQuestions.get(i).getId(), i + 1);
        }

        Map<Long, Integer> scores = request.getScores() != null ? request.getScores() : Map.of();
        Map<Long, String> texts = request.getTexts() != null ? request.getTexts() : Map.of();
        List<Integer> missingNumbers = orderedQuestions.stream()
                .filter(q -> {
                    if (q.isScale()) {
                        return scores.get(q.getId()) == null;
                    }
                    if ("DESCRIPTIVE".equals(q.getQuestionType())) {
                        String value = texts.get(q.getId());
                        return value == null || value.isBlank();
                    }
                    return false;
                })
                .map(q -> numberByQuestionId.get(q.getId()))
                .toList();

        if (!missingNumbers.isEmpty()) {
            String joined = missingNumbers.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "미응답 문항이 있습니다. 문항 번호: " + joined
            );
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
