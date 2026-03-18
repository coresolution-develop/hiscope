package com.hiscope.evaluation.domain.organization.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import com.hiscope.evaluation.domain.organization.enums.OrganizationProfile;
import com.hiscope.evaluation.domain.organization.enums.OrganizationType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false, length = 20)
    @Builder.Default
    private OrganizationType organizationType = OrganizationType.HOSPITAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_profile", nullable = false, length = 30)
    @Builder.Default
    private OrganizationProfile organizationProfile = OrganizationProfile.HOSPITAL_DEFAULT;

    public void update(String name, String status, OrganizationType organizationType, OrganizationProfile organizationProfile) {
        this.name = name;
        this.status = status;
        this.organizationType = organizationType;
        this.organizationProfile = organizationProfile;
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }
}
