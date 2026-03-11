package com.hiscope.evaluation.common.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Builder
public class CustomUserDetails implements UserDetails {

    private final Long id;               // Account.id 또는 UserAccount.id
    private final String loginId;
    private final String password;
    private final Long organizationId;   // null = 슈퍼 관리자
    private final Long employeeId;       // null = 관리자 계정
    private final String role;           // ROLE_SUPER_ADMIN | ROLE_ORG_ADMIN | ROLE_USER
    private final String name;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }

    public boolean isSuperAdmin() {
        return "ROLE_SUPER_ADMIN".equals(role);
    }

    public boolean isOrgAdmin() {
        return "ROLE_ORG_ADMIN".equals(role);
    }

    public boolean isUser() {
        return "ROLE_USER".equals(role);
    }
}
