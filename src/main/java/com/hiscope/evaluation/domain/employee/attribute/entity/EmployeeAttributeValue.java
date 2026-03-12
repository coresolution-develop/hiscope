package com.hiscope.evaluation.domain.employee.attribute.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employee_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "attribute_id", "value_text"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmployeeAttributeValue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attribute_id", nullable = false)
    private Long attributeId;

    @Column(name = "value_text", nullable = false, length = 300)
    private String valueText;

    public void updateValue(String valueText) {
        this.valueText = valueText;
    }
}
