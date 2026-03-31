package com.hiscope.evaluation.domain.evaluation.session.repository;

import com.hiscope.evaluation.domain.evaluation.session.entity.SessionParticipantOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionParticipantOverrideRepository extends JpaRepository<SessionParticipantOverride, Long> {

    List<SessionParticipantOverride> findAllBySessionId(Long sessionId);

    List<SessionParticipantOverride> findAllBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Optional<SessionParticipantOverride> findTopBySessionIdAndEmployeeIdOrderByCreatedAtDesc(
            Long sessionId, Long employeeId);
}
