package com.hiscope.evaluation.domain.account.dto;

import com.hiscope.evaluation.domain.account.entity.Account;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AccountResponse {

    private Long id;
    private Long organizationId;
    private String loginId;
    private String name;
    private String email;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .organizationId(account.getOrganizationId())
                .loginId(account.getLoginId())
                .name(account.getName())
                .email(account.getEmail())
                .role(account.getRole())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
