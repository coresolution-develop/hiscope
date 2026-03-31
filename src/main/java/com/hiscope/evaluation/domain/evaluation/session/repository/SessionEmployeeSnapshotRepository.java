package com.hiscope.evaluation.domain.evaluation.session.repository;

import com.hiscope.evaluation.domain.evaluation.session.entity.SessionEmployeeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionEmployeeSnapshotRepository extends JpaRepository<SessionEmployeeSnapshot, Long> {

    List<SessionEmployeeSnapshot> findAllBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    boolean existsBySessionIdAndEmployeeId(Long sessionId, Long employeeId);

    Optional<SessionEmployeeSnapshot> findBySessionIdAndEmployeeId(Long sessionId, Long employeeId);
}
