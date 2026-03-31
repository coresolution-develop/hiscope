package com.hiscope.evaluation.domain.evaluation.rule.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.relationship.entity.EvaluationRelationship;
import com.hiscope.evaluation.domain.evaluation.relationship.repository.EvaluationRelationshipRepository;
import com.hiscope.evaluation.domain.evaluation.rule.dto.DepartmentSimpleView;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipByDeptRequest;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipPreviewItem;
import com.hiscope.evaluation.domain.evaluation.rule.dto.SimpleRelationshipPreviewResult;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimpleRelationshipWizardService {

    private static final int PREVIEW_MAX = 300;
    private static final String SOURCE_WIZARD = "WIZARD_GENERATED";

    private final EvaluationSessionRepository sessionRepository;
    private final EvaluationRelationshipRepository relationshipRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public List<DepartmentSimpleView> getAvailableDepartments(Long orgId) {
        var depts = departmentRepository.findByOrganizationIdAndActiveOrderByNameAsc(orgId, true);
        List<Employee> activeEmps = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE");

        Map<Long, Long> totalByDept = activeEmps.stream()
                .filter(e -> e.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Employee::getDepartmentId, Collectors.counting()));
        Map<Long, Long> leaderByDept = activeEmps.stream()
                .filter(e -> e.getDepartmentId() != null && e.isTeamLeader())
                .collect(Collectors.groupingBy(Employee::getDepartmentId, Collectors.counting()));

        return depts.stream()
                .map(d -> new DepartmentSimpleView(
                        d.getId(),
                        d.getName(),
                        totalByDept.getOrDefault(d.getId(), 0L).intValue(),
                        leaderByDept.getOrDefault(d.getId(), 0L).intValue()
                ))
                .toList();
    }

    public SimpleRelationshipPreviewResult preview(Long sessionId, Long orgId, SimpleRelationshipByDeptRequest req) {
        Set<Long> allDeptIds = new HashSet<>();
        if (req.isUpwardEnabled() && req.getUpwardDeptIds() != null) allDeptIds.addAll(req.getUpwardDeptIds());
        if (req.isPeerEnabled() && req.getPeerDeptIds() != null) allDeptIds.addAll(req.getPeerDeptIds());
        if (req.isDownwardEnabled() && req.getDownwardDeptIds() != null) allDeptIds.addAll(req.getDownwardDeptIds());

        if (allDeptIds.isEmpty()) {
            return new SimpleRelationshipPreviewResult(0, false, List.of(), List.of());
        }

        List<Employee> employees = employeeRepository.findActiveByOrganizationIdAndDepartmentIdIn(orgId, allDeptIds);
        Map<Long, List<Employee>> byDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDepartmentId));
        Map<Long, String> deptNameMap = departmentRepository.findAllById(allDeptIds).stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getName()));

        List<SimpleRelationshipPreviewItem> items = new ArrayList<>();
        Set<String> dedupKeys = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        if (req.isUpwardEnabled() && req.getUpwardDeptIds() != null) {
            for (Long deptId : req.getUpwardDeptIds()) {
                List<Employee> emps = byDept.getOrDefault(deptId, List.of());
                List<Employee> leaders = emps.stream().filter(Employee::isTeamLeader).toList();
                List<Employee> members = emps.stream().filter(e -> !e.isTeamLeader()).toList();
                if (leaders.isEmpty()) {
                    warnings.add(deptNameMap.getOrDefault(deptId, "ID:" + deptId) + " 부서에 부서장이 없어 상향 평가를 생성할 수 없습니다.");
                    continue;
                }
                addPairs(items, dedupKeys, "UPWARD", members, leaders, deptNameMap);
            }
        }

        if (req.isPeerEnabled() && req.getPeerDeptIds() != null) {
            for (Long deptId : req.getPeerDeptIds()) {
                List<Employee> members = byDept.getOrDefault(deptId, List.of()).stream()
                        .filter(e -> !e.isTeamLeader()).toList();
                if (members.size() < 2) continue;
                for (int i = 0; i < members.size(); i++) {
                    for (int j = i + 1; j < members.size(); j++) {
                        Employee a = members.get(i);
                        Employee b = members.get(j);
                        String deptName = deptNameMap.getOrDefault(deptId, "-");
                        addSingle(items, dedupKeys, "PEER", a, b, deptName, deptName);
                        addSingle(items, dedupKeys, "PEER", b, a, deptName, deptName);
                    }
                }
            }
        }

        if (req.isDownwardEnabled() && req.getDownwardDeptIds() != null) {
            for (Long deptId : req.getDownwardDeptIds()) {
                List<Employee> emps = byDept.getOrDefault(deptId, List.of());
                List<Employee> leaders = emps.stream().filter(Employee::isTeamLeader).toList();
                List<Employee> members = emps.stream().filter(e -> !e.isTeamLeader()).toList();
                if (leaders.isEmpty()) {
                    String deptName = deptNameMap.getOrDefault(deptId, "ID:" + deptId);
                    boolean alreadyWarned = warnings.stream().anyMatch(w -> w.startsWith(deptName));
                    if (!alreadyWarned) {
                        warnings.add(deptName + " 부서에 부서장이 없어 하향 평가를 생성할 수 없습니다.");
                    }
                    continue;
                }
                addPairs(items, dedupKeys, "DOWNWARD", leaders, members, deptNameMap);
            }
        }

        boolean truncated = items.size() > PREVIEW_MAX;
        return new SimpleRelationshipPreviewResult(
                items.size(),
                truncated,
                truncated ? items.subList(0, PREVIEW_MAX) : items,
                warnings
        );
    }

    @Transactional
    public void apply(Long sessionId, Long orgId, Long accountId, SimpleRelationshipByDeptRequest req) {
        EvaluationSession session = sessionRepository.findByOrganizationIdAndId(orgId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.isPending()) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED, "대기 상태의 세션에만 간편 설정을 적용할 수 있습니다.");
        }

        SimpleRelationshipPreviewResult result = preview(sessionId, orgId, req);

        relationshipRepository.deleteBySessionIdAndSource(sessionId, SOURCE_WIZARD);

        for (SimpleRelationshipPreviewItem item : result.items()) {
            if (relationshipRepository.existsBySessionIdAndEvaluatorIdAndEvaluateeId(
                    sessionId, item.evaluatorId(), item.evaluateeId())) {
                continue;
            }
            relationshipRepository.save(EvaluationRelationship.builder()
                    .sessionId(sessionId)
                    .organizationId(orgId)
                    .evaluatorId(item.evaluatorId())
                    .evaluateeId(item.evaluateeId())
                    .relationType(item.relationType())
                    .source(SOURCE_WIZARD)
                    .active(true)
                    .build());
        }

        log.info("간편 설정 적용 완료: sessionId={}, count={}", sessionId, result.items().size());
    }

    private void addPairs(List<SimpleRelationshipPreviewItem> items, Set<String> dedupKeys,
                          String type, List<Employee> evaluators, List<Employee> evaluatees,
                          Map<Long, String> deptNameMap) {
        for (Employee evaluator : evaluators) {
            for (Employee evaluatee : evaluatees) {
                if (evaluator.getId().equals(evaluatee.getId())) continue;
                addSingle(items, dedupKeys, type, evaluator, evaluatee,
                        deptNameMap.getOrDefault(evaluator.getDepartmentId(), "-"),
                        deptNameMap.getOrDefault(evaluatee.getDepartmentId(), "-"));
            }
        }
    }

    private void addSingle(List<SimpleRelationshipPreviewItem> items, Set<String> dedupKeys,
                           String type, Employee evaluator, Employee evaluatee,
                           String evaluatorDept, String evaluateeDept) {
        String key = evaluator.getId() + "_" + evaluatee.getId();
        if (!dedupKeys.add(key)) return;
        items.add(new SimpleRelationshipPreviewItem(
                type,
                evaluator.getId(), evaluator.getName(), evaluatorDept, evaluator.getJobTitle(),
                evaluatee.getId(), evaluatee.getName(), evaluateeDept, evaluatee.getJobTitle()
        ));
    }
}
