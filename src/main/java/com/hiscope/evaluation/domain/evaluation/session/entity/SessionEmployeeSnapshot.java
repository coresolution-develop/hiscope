package com.hiscope.evaluation.domain.evaluation.session.entity;

import com.hiscope.evaluation.domain.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_employee_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionEmployeeSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String departmentName;

    /** Java 필드명 position, DB 컬럼명 position_name */
    @Column(name = "position_name", length = 100)
    private String position;

    @Column(length = 100)
    private String jobTitle;

    @Column(nullable = false, updatable = false)
    private LocalDateTime snapshottedAt;

    @PrePersist
    protected void onCreate() {
        this.snapshottedAt = LocalDateTime.now();
    }

    public static SessionEmployeeSnapshot of(
            Long sessionId, Employee employee, String departmentName) {
        SessionEmployeeSnapshot s = new SessionEmployeeSnapshot();
        s.sessionId      = sessionId;
        s.employeeId     = employee.getId();
        s.name           = employee.getName();
        s.departmentName = departmentName;
        s.position       = employee.getPosition();
        s.jobTitle       = employee.getJobTitle();
        return s;
    }
}
