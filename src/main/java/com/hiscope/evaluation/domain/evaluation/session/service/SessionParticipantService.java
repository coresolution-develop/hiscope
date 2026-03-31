package com.hiscope.evaluation.domain.evaluation.session.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.department.entity.Department;
import com.hiscope.evaluation.domain.department.repository.DepartmentRepository;
import com.hiscope.evaluation.domain.employee.entity.Employee;
import com.hiscope.evaluation.domain.employee.repository.EmployeeRepository;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.ParticipantSearchResult;
import com.hiscope.evaluation.domain.evaluation.session.dto.view.SessionParticipantView;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.entity.SessionEmployeeSnapshot;
import com.hiscope.evaluation.domain.evaluation.session.entity.SessionParticipantOverride;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.session.repository.SessionEmployeeSnapshotRepository;
import com.hiscope.evaluation.domain.evaluation.session.repository.SessionParticipantOverrideRepository;
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
public class SessionParticipantService {

    private final SessionEmployeeSnapshotRepository snapshotRepository;
    private final SessionParticipantOverrideRepository overrideRepository;
    private final EvaluationSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    // ─────────────────────────────────────────────────────────
    // 1. 스냅샷 생성 (세션 확정)
    // ─────────────────────────────────────────────────────────

    @Transactional
    public void snapshotParticipants(Long sessionId, Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        validateSession(orgId, sessionId);

        // 이미 스냅샷 존재 시 → 로그만 남기고 early return (방어 로직 중복 호출 허용)
        if (snapshotRepository.existsBySessionId(sessionId)) {
            log.info("세션 {} 스냅샷이 이미 존재합니다 — 생성 건너뜀", sessionId);
            return;
        }

        // 해당 기관 ACTIVE 직원 전체 조회
        List<Employee> employees = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE");

        // 부서명 IN 쿼리 조회
        Set<Long> deptIds = employees.stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of() :
                departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        // bulk INSERT
        List<SessionEmployeeSnapshot> snapshots = employees.stream()
                .map(e -> SessionEmployeeSnapshot.of(
                        sessionId, e,
                        e.getDepartmentId() != null ? deptNameMap.get(e.getDepartmentId()) : null))
                .toList();
        snapshotRepository.saveAll(snapshots);
        log.info("세션 {} 참여자 스냅샷 생성 완료 — {}명", sessionId, snapshots.size());
    }

    public boolean hasSnapshot(Long sessionId) {
        return snapshotRepository.existsBySessionId(sessionId);
    }

    // ─────────────────────────────────────────────────────────
    // 2. 최종 참여자 목록 조회 (스냅샷 + override 합산)
    // ─────────────────────────────────────────────────────────

    public List<SessionParticipantView> getParticipants(Long sessionId, Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        validateSession(orgId, sessionId);

        List<SessionEmployeeSnapshot> snapshots = snapshotRepository.findAllBySessionId(sessionId);
        List<SessionParticipantOverride> overrides = overrideRepository.findAllBySessionId(sessionId);

        // 직원별 최신 override (createdAt 기준)
        Map<Long, SessionParticipantOverride> latestOverride = overrides.stream()
                .collect(Collectors.toMap(
                        SessionParticipantOverride::getEmployeeId,
                        o -> o,
                        (a, b) -> b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a
                ));

        List<SessionParticipantView> result = new ArrayList<>();
        Set<Long> snapshotEmpIds = new HashSet<>();

        // 스냅샷 기반 처리
        for (SessionEmployeeSnapshot snap : snapshots) {
            snapshotEmpIds.add(snap.getEmployeeId());
            SessionParticipantOverride ovr = latestOverride.get(snap.getEmployeeId());
            String name = (ovr != null && ovr.getOverrideName() != null)
                    ? ovr.getOverrideName() : snap.getName();
            String dept = (ovr != null && ovr.getOverrideDepartmentName() != null)
                    ? ovr.getOverrideDepartmentName() : snap.getDepartmentName();
            String pos  = (ovr != null && ovr.getOverridePositionName() != null)
                    ? ovr.getOverridePositionName() : snap.getPosition();
            boolean active = (ovr == null || ovr.getAction() != SessionParticipantOverride.Action.REMOVE);
            result.add(new SessionParticipantView(
                    snap.getEmployeeId(), name, dept, pos, active,
                    ovr != null ? ovr.getAction().name() : null,
                    ovr != null ? ovr.getReason() : null
            ));
        }

        // ADD override 중 스냅샷에 없는 직원 추가
        for (SessionParticipantOverride ovr : latestOverride.values()) {
            if (ovr.getAction() == SessionParticipantOverride.Action.ADD
                    && !snapshotEmpIds.contains(ovr.getEmployeeId())) {
                result.add(new SessionParticipantView(
                        ovr.getEmployeeId(),
                        ovr.getOverrideName(),
                        ovr.getOverrideDepartmentName(),
                        ovr.getOverridePositionName(),
                        true, "ADD", ovr.getReason()
                ));
            }
        }

        // 정렬: 활성 우선, 같은 그룹 내 이름순, 제외자 마지막
        result.sort(Comparator.comparing(SessionParticipantView::isActive).reversed()
                .thenComparing(SessionParticipantView::name, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 3. 직원 추가 (ADD override)
    // ─────────────────────────────────────────────────────────

    @Transactional
    public void addParticipant(Long sessionId, Long employeeId, String reason,
                               Long orgId, Long accountId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = validateSession(orgId, sessionId);
        if (session.isClosed()) {
            throw new BusinessException(ErrorCode.SESSION_CLOSED, "종료된 세션에는 참여자를 추가할 수 없습니다.");
        }

        Employee employee = employeeRepository.findByOrganizationIdAndId(orgId, employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비활성 직원은 추가할 수 없습니다.");
        }

        // 이미 활성 참여자인지 확인
        if (isCurrentlyActive(sessionId, employeeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 평가에 포함된 직원입니다.");
        }

        // 추가 시점 현재 부서명 조회
        String deptName = null;
        if (employee.getDepartmentId() != null) {
            deptName = departmentRepository.findById(employee.getDepartmentId())
                    .map(Department::getName).orElse(null);
        }

        overrideRepository.save(SessionParticipantOverride.of(
                sessionId, employeeId, SessionParticipantOverride.Action.ADD,
                employee.getName(), deptName, employee.getPosition(),
                reason, accountId
        ));
    }

    // ─────────────────────────────────────────────────────────
    // 4. 직원 제외 (REMOVE override)
    // ─────────────────────────────────────────────────────────

    @Transactional
    public void removeParticipant(Long sessionId, Long employeeId, String reason,
                                  Long orgId, Long accountId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationSession session = validateSession(orgId, sessionId);
        if (session.isClosed()) {
            throw new BusinessException(ErrorCode.SESSION_CLOSED, "종료된 세션에서는 참여자를 제외할 수 없습니다.");
        }

        if (!isCurrentlyActive(sessionId, employeeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "참여자 목록에 없는 직원이거나 이미 제외된 직원입니다.");
        }

        // EvaluationAssignment에 CANCELLED 상태 없음 → override만 생성 (기존 응답 데이터 보존)
        overrideRepository.save(SessionParticipantOverride.of(
                sessionId, employeeId, SessionParticipantOverride.Action.REMOVE,
                null, null, null,
                reason, accountId
        ));
    }

    // ─────────────────────────────────────────────────────────
    // 5. override 이력 조회
    // ─────────────────────────────────────────────────────────

    public List<SessionParticipantOverride> getOverrideHistory(Long sessionId, Long orgId) {
        SecurityUtils.checkOrgAccess(orgId);
        validateSession(orgId, sessionId);
        return overrideRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    // ─────────────────────────────────────────────────────────
    // 6. 직원 검색 (추가 모달용, 이미 활성 참여자 제외)
    // ─────────────────────────────────────────────────────────

    public List<ParticipantSearchResult> searchEligibleEmployees(
            Long sessionId, Long orgId, String keyword) {
        SecurityUtils.checkOrgAccess(orgId);

        // 현재 활성 참여자 ID 수집
        Set<Long> activeIds = getParticipants(sessionId, orgId).stream()
                .filter(SessionParticipantView::isActive)
                .map(SessionParticipantView::employeeId)
                .collect(Collectors.toSet());

        // 직원 검색 (keyword 없으면 전체)
        List<Employee> candidates;
        if (keyword == null || keyword.isBlank()) {
            candidates = employeeRepository.findByOrganizationIdAndStatusOrderByNameAsc(orgId, "ACTIVE");
        } else {
            List<Long> matchIds = employeeRepository.findIdsByOrganizationIdAndKeyword(orgId, keyword.trim());
            candidates = matchIds.isEmpty() ? List.of() :
                    employeeRepository.findAllById(matchIds).stream()
                            .filter(Employee::isActive).toList();
        }

        // 부서명 일괄 조회
        Set<Long> deptIds = candidates.stream()
                .map(Employee::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of() :
                departmentRepository.findAllById(deptIds).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName));

        return candidates.stream()
                .filter(e -> !activeIds.contains(e.getId()))
                .limit(20)
                .map(e -> new ParticipantSearchResult(
                        e.getId(), e.getName(),
                        e.getDepartmentId() != null ? deptNameMap.get(e.getDepartmentId()) : null,
                        e.getPosition()
                ))
                .toList();
    }

    // ─────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────

    private EvaluationSession validateSession(Long orgId, Long sessionId) {
        return sessionRepository.findByOrganizationIdAndId(orgId, sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private boolean isCurrentlyActive(Long sessionId, Long employeeId) {
        boolean inSnapshot = snapshotRepository.existsBySessionIdAndEmployeeId(sessionId, employeeId);
        Optional<SessionParticipantOverride> latest = overrideRepository
                .findTopBySessionIdAndEmployeeIdOrderByCreatedAtDesc(sessionId, employeeId);

        if (inSnapshot) {
            // 스냅샷에 있고 최신 override가 REMOVE가 아니면 활성
            return latest.isEmpty() || latest.get().getAction() != SessionParticipantOverride.Action.REMOVE;
        } else {
            // 스냅샷에 없고 최신 override가 ADD이면 활성
            return latest.isPresent() && latest.get().getAction() == SessionParticipantOverride.Action.ADD;
        }
    }
}
