package com.hiscope.evaluation.domain.account.entity;

import com.hiscope.evaluation.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null = 슈퍼 관리자
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "login_id", nullable = false, length = 100)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateProfile(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void markPasswordChangeRequired() {
        this.mustChangePassword = true;
    }

    public void clearPasswordChangeRequired() {
        this.mustChangePassword = false;
    }
}
