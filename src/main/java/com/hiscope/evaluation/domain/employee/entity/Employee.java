package com.hiscope.evaluation.domain.employee.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "employee_number", length = 50)
    private String employeeNumber;

    /** 직위: 사원, 대리, 과장, 차장, 부장 */
    @Column(length = 50)
    private String position;

    /** 직책: 팀원, 팀장, 실장, 부문장 */
    @Column(name = "job_title", length = 50)
    private String jobTitle;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    public void update(Long departmentId, String name, String position, String jobTitle,
                       String email, String status) {
        this.departmentId = departmentId;
        this.name = name;
        this.position = position;
        this.jobTitle = jobTitle;
        this.email = email;
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    public boolean isTeamLeader() {
        return "팀장".equals(this.jobTitle) || "실장".equals(this.jobTitle) || "부문장".equals(this.jobTitle);
    }
}
