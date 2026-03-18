package com.hiscope.evaluation.domain.employee.repository;

import com.hiscope.evaluation.domain.employee.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    Optional<UserAccount> findByOrganizationIdAndLoginId(Long organizationId, String loginId);

    boolean existsByOrganizationIdAndLoginId(Long organizationId, String loginId);

    Optional<UserAccount> findByEmployeeId(Long employeeId);
    List<UserAccount> findByEmployeeIdIn(List<Long> employeeIds);

    /** 기관 내 UserAccount 목록 — EmployeeUploadHandler loginId 중복 검사용 (org 격리) */
    List<UserAccount> findByOrganizationId(Long organizationId);

    /**
     * 기관 내 UserAccount + Employee JOIN FETCH.
     * EmployeeService.findAll() 에서 findAll() + 메모리 필터 대신 사용.
     * N+1 방지 및 DB 레벨 org 격리.
     */
    @Query("SELECT ua FROM UserAccount ua JOIN FETCH ua.employee WHERE ua.organizationId = :orgId")
    List<UserAccount> findByOrganizationIdWithEmployee(@Param("orgId") Long orgId);

    /**
     * 로그인 처리 전용 JOIN FETCH 쿼리.
     * (1) Employee lazy loading 제거
     * (2) status = 'ACTIVE' 인 직원만 로그인 허용 (비활성/휴직 직원 차단)
     */
    @Query("SELECT ua FROM UserAccount ua JOIN FETCH ua.employee e " +
           "WHERE ua.loginId = :loginId AND e.status = 'ACTIVE'")
    Optional<UserAccount> findByLoginIdAndEmployeeActive(@Param("loginId") String loginId);

    @Query("SELECT ua FROM UserAccount ua JOIN FETCH ua.employee e " +
            "WHERE ua.organizationId = :orgId AND ua.loginId = :loginId AND e.status = 'ACTIVE'")
    Optional<UserAccount> findByOrganizationIdAndLoginIdAndEmployeeActive(@Param("orgId") Long orgId,
                                                                           @Param("loginId") String loginId);

    @Query("SELECT ua FROM UserAccount ua JOIN FETCH ua.employee e " +
            "WHERE ua.loginId = :loginId AND e.status = 'ACTIVE'")
    List<UserAccount> findAllByLoginIdAndEmployeeActive(@Param("loginId") String loginId);
}
