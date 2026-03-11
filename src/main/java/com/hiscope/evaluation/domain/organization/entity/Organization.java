package com.hiscope.evaluation.domain.organization.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Organization extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    public void update(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }
}
