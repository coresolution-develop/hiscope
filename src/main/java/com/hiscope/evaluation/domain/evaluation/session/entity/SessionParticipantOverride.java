package com.hiscope.evaluation.domain.evaluation.session.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_participant_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionParticipantOverride {

    public enum Action { ADD, REMOVE, UPDATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Action action;

    @Column(length = 100)
    private String overrideName;

    @Column(length = 100)
    private String overrideDepartmentName;

    @Column(length = 100)
    private String overridePositionName;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static SessionParticipantOverride of(
            Long sessionId, Long employeeId, Action action,
            String overrideName, String overrideDepartmentName, String overridePositionName,
            String reason, Long createdBy) {
        SessionParticipantOverride o = new SessionParticipantOverride();
        o.sessionId               = sessionId;
        o.employeeId              = employeeId;
        o.action                  = action;
        o.overrideName            = overrideName;
        o.overrideDepartmentName  = overrideDepartmentName;
        o.overridePositionName    = overridePositionName;
        o.reason                  = reason;
        o.createdBy               = createdBy;
        return o;
    }
}
