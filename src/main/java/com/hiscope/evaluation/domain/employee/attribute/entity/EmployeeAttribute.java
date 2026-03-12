package com.hiscope.evaluation.domain.employee.attribute.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employee_attributes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "attribute_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmployeeAttribute extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "attribute_key", nullable = false, length = 100)
    private String attributeKey;

    @Column(name = "attribute_name", nullable = false, length = 200)
    private String attributeName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public void rename(String attributeName) {
        this.attributeName = attributeName;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
