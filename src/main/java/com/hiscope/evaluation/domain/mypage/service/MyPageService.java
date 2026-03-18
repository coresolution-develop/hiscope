package com.hiscope.evaluation.domain.mypage.service;

import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponseItem;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.mypage.dto.MyPageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final EvaluationAssignmentRepository assignmentRepository;
    private final EvaluationResponseRepository responseRepository;
    private final EvaluationResponseItemRepository itemRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AiSummaryService aiSummaryService;

    /**
     * 사용자 마이페이지 집계 기준
     * - 기준 축: 내가 "받은" 평가(피평가자 evaluatee 기준)
     * - 평균 비교군: 내가 받은 평가가 속한 동일 세션들의 조직 내 최종 제출 응답 전체
     */
    public MyPageView getMyPage(Long orgId, Long employeeId) {
        Employee me = employeeRepository.findByOrganizationIdAndId(orgId, employeeId).orElse(null);
        if (me == null) {
            return new MyPageView(
                    new MyPageView.Profile(employeeId, "-", "사용자", "-", "-", "-"),
                    new MyPageView.Summary(0, 0, 0, 0, 0, 0),
                    List.of(),
                    List.of(),
                    List.of(),
                    new MyPageView.AiSummary(
                            List.of("직원 기본 정보가 확인되지 않았습니다."),
                            List.of("관리자에게 계정-직원 매핑 상태를 확인해 주세요."),
                            "매핑 정보가 정리되면 평가 요약이 표시됩니다."
                    ),
                    aiSummaryService.mode()
            );
        }
        String departmentName = me.getDepartmentId() == null
                ? "-"
                : departmentRepository.findByOrganizationIdAndId(orgId, me.getDepartmentId())
                .map(Department::getName)
                .orElse("-");

        List<EvaluationAssignment> receivedAssignments = assignmentRepository
                .findByOrganizationIdAndEvaluateeIdOrderByCreatedAtDesc(orgId, employeeId)
                .stream()
                .filter(EvaluationAssignment::isSubmitted)
                .toList();

        if (receivedAssignments.isEmpty()) {
            return new MyPageView(
                    new MyPageView.Profile(me.getId(), me.getEmployeeNumber(), me.getName(), departmentName, me.getPosition(), me.getJobTitle()),
                    new MyPageView.Summary(0, 0, 0, 0, 0, 0),
                    List.of(),
                    List.of(),
                    List.of(),
                    new MyPageView.AiSummary(
                            List.of("제출된 평가 데이터가 없습니다."),
                            List.of("평가가 완료되면 분석 결과가 표시됩니다."),
                            "평가 데이터가 누적되면 AI 요약이 생성됩니다."
                    ),
                    aiSummaryService.mode()
            );
        }

        List<Long> assignmentIds = receivedAssignments.stream().map(EvaluationAssignment::getId).toList();
        List<EvaluationResponse> responses = responseRepository
                .findByOrganizationIdAndFinalSubmitTrueAndAssignmentIdIn(orgId, assignmentIds);
        Map<Long, EvaluationResponse> responseByAssignment = responses.stream()
                .collect(Collectors.toMap(EvaluationResponse::getAssignmentId, r -> r, this::pickLatestResponse));

        Map<Long, List<EvaluationResponseItem>> myItemsByResponse = groupItemsByResponseId(responses);
        List<EvaluationResponseItem> myItems = myItemsByResponse.values().stream().flatMap(Collection::stream).toList();
        List<EvaluationResponseItem> myScoreItems = myItems.stream().filter(i -> i.getScoreValue() != null).toList();
        List<EvaluationResponseItem> myTextItems = myItems.stream().filter(i -> hasText(i.getTextValue())).toList();

        Set<Long> myQuestionIds = myItems.stream().map(EvaluationResponseItem::getQuestionId).collect(Collectors.toSet());
        Map<Long, EvaluationQuestion> questionMap = questionRepository.findAllById(myQuestionIds).stream()
                .collect(Collectors.toMap(EvaluationQuestion::getId, q -> q));

        List<Long> sessionIds = receivedAssignments.stream().map(EvaluationAssignment::getSessionId).distinct().toList();
        List<EvaluationAssignment> orgAssignments = sessionIds.isEmpty()
                ? List.of()
                : assignmentRepository.findByOrganizationIdAndSessionIdIn(orgId, sessionIds);
        List<Long> orgAssignmentIds = orgAssignments.stream().map(EvaluationAssignment::getId).toList();
        List<EvaluationResponse> orgResponses = orgAssignmentIds.isEmpty()
                ? List.of()
                : responseRepository.findByOrganizationIdAndFinalSubmitTrueAndAssignmentIdIn(orgId, orgAssignmentIds);
        Map<Long, List<EvaluationResponseItem>> orgItemsByResponse = groupItemsByResponseId(orgResponses);
        List<EvaluationResponseItem> orgScoreItems = orgItemsByResponse.values().stream()
                .flatMap(Collection::stream)
                .filter(i -> i.getScoreValue() != null)
                .toList();

        Map<Long, Double> myAvgByQuestion = averageByQuestion(myScoreItems);
        Map<Long, Double> orgAvgByQuestion = averageByQuestion(orgScoreItems);
        Map<Long, Long> myScoreCountByQuestion = myScoreItems.stream()
                .collect(Collectors.groupingBy(EvaluationResponseItem::getQuestionId, Collectors.counting()));
        Map<Long, String> myCommentsByQuestion = myTextItems.stream()
                .collect(Collectors.groupingBy(EvaluationResponseItem::getQuestionId,
                        Collectors.mapping(EvaluationResponseItem::getTextValue,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        comments -> comments.stream()
                                                .filter(this::hasText)
                                                .map(String::trim)
                                                .limit(3)
                                                .collect(Collectors.joining(" | "))))));

        List<MyPageView.QuestionScore> questionScores = new ArrayList<>();
        for (Long questionId : myQuestionIds) {
            EvaluationQuestion q = questionMap.get(questionId);
            if (q == null) {
                continue;
            }
            double myAvg = round(myAvgByQuestion.getOrDefault(questionId, 0.0));
            Double orgAvgRaw = orgAvgByQuestion.get(questionId);
            double orgAvg = round(orgAvgRaw != null ? orgAvgRaw : 0.0);
            double delta = round(myAvg - orgAvg);

            int responseCount = myScoreCountByQuestion.getOrDefault(questionId, 0L).intValue();
            String comments = myCommentsByQuestion.getOrDefault(questionId, "");

            questionScores.add(new MyPageView.QuestionScore(
                    questionId,
                    defaultCategory(q.getCategory()),
                    q.getContent(),
                    myAvg,
                    orgAvg,
                    delta,
                    responseCount,
                    comments
            ));
        }
        questionScores = questionScores.stream()
                .sorted(Comparator.comparing(MyPageView.QuestionScore::category, Comparator.nullsLast(String::compareTo))
                        .thenComparing(MyPageView.QuestionScore::question, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<String, Double> myByCategory = averageByCategory(myScoreItems, questionMap);
        Map<String, Double> orgByCategory = averageByCategory(orgScoreItems, questionMap);
        List<MyPageView.CategoryScore> categoryScores = myByCategory.entrySet().stream()
                .map(entry -> {
                    String category = entry.getKey();
                    double myAvg = round(entry.getValue());
                    double orgAvg = round(orgByCategory.getOrDefault(category, 0.0));
                    return new MyPageView.CategoryScore(category, myAvg, orgAvg, round(myAvg - orgAvg));
                })
                .sorted(Comparator.comparing(MyPageView.CategoryScore::category, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<Long, String> sessionNameMap = sessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(EvaluationSession::getId, EvaluationSession::getName, (a, b) -> a));
        Map<Long, String> evaluatorNameMap = employeeRepository.findAllById(
                        receivedAssignments.stream().map(EvaluationAssignment::getEvaluatorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));

        List<MyPageView.EvaluationResult> evaluations = receivedAssignments.stream()
                .map(a -> {
                    EvaluationResponse response = responseByAssignment.get(a.getId());
                    if (response == null) {
                        return null;
                    }
                    List<EvaluationResponseItem> items = myItemsByResponse.getOrDefault(response.getId(), List.of());
                    List<Integer> scores = items.stream()
                            .map(EvaluationResponseItem::getScoreValue)
                            .filter(Objects::nonNull)
                            .toList();
                    Double avgScore = scores.isEmpty() ? null : round(scores.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                    String commentPreview = items.stream()
                            .map(EvaluationResponseItem::getTextValue)
                            .filter(this::hasText)
                            .map(String::trim)
                            .findFirst()
                            .orElse("-");
                    if (commentPreview.length() > 100) {
                        commentPreview = commentPreview.substring(0, 100) + "...";
                    }
                    return new MyPageView.EvaluationResult(
                            a.getId(),
                            sessionNameMap.getOrDefault(a.getSessionId(), "세션 " + a.getSessionId()),
                            evaluatorNameMap.getOrDefault(a.getEvaluatorId(), "-"),
                            a.getSubmittedAt(),
                            avgScore,
                            commentPreview
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        double myOverall = round(myScoreItems.stream().mapToInt(EvaluationResponseItem::getScoreValue).average().orElse(0.0));
        double orgOverall = round(orgScoreItems.stream().mapToInt(EvaluationResponseItem::getScoreValue).average().orElse(0.0));
        MyPageView.Summary summary = new MyPageView.Summary(
                myOverall,
                orgOverall,
                round(myOverall - orgOverall),
                evaluations.size(),
                myScoreItems.size(),
                myTextItems.size()
        );

        return new MyPageView(
                new MyPageView.Profile(me.getId(), me.getEmployeeNumber(), me.getName(), departmentName, me.getPosition(), me.getJobTitle()),
                summary,
                evaluations,
                questionScores,
                categoryScores,
                aiSummaryService.summarize(categoryScores, myOverall, orgOverall, myTextItems.size()),
                aiSummaryService.mode()
        );
    }

    private Map<Long, List<EvaluationResponseItem>> groupItemsByResponseId(List<EvaluationResponse> responses) {
        if (responses.isEmpty()) {
            return Map.of();
        }
        return itemRepository.findByResponseIdIn(responses.stream().map(EvaluationResponse::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(EvaluationResponseItem::getResponseId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, Double> averageByQuestion(List<EvaluationResponseItem> items) {
        return items.stream()
                .collect(Collectors.groupingBy(EvaluationResponseItem::getQuestionId,
                        Collectors.averagingInt(EvaluationResponseItem::getScoreValue)));
    }

    private Map<String, Double> averageByCategory(List<EvaluationResponseItem> items,
                                                  Map<Long, EvaluationQuestion> questionMap) {
        return items.stream()
                .filter(item -> questionMap.containsKey(item.getQuestionId()))
                .collect(Collectors.groupingBy(item -> defaultCategory(questionMap.get(item.getQuestionId()).getCategory()),
                        Collectors.averagingInt(EvaluationResponseItem::getScoreValue)));
    }

    private String defaultCategory(String category) {
        return (category == null || category.isBlank()) ? "기타" : category;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private EvaluationResponse pickLatestResponse(EvaluationResponse left, EvaluationResponse right) {
        LocalDateTime l = left.getSubmittedAt();
        LocalDateTime r = right.getSubmittedAt();
        if (l == null) return right;
        if (r == null) return left;
        return l.isAfter(r) ? left : right;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
