package com.hiscope.evaluation.domain.upload.repository;

import com.hiscope.evaluation.domain.upload.entity.UploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UploadHistoryRepository extends JpaRepository<UploadHistory, Long> {

    List<UploadHistory> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    List<UploadHistory> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);

    List<UploadHistory> findByOrganizationIdAndUploadTypeOrderByCreatedAtDesc(Long organizationId, String uploadType);
}
