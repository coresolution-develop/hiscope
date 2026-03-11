package com.hiscope.evaluation.domain.upload.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UploadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "upload_type", nullable = false, length = 30)
    private String uploadType; // DEPARTMENT, EMPLOYEE, QUESTION

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "success_rows", nullable = false)
    @Builder.Default
    private int successRows = 0;

    @Column(name = "fail_rows", nullable = false)
    @Builder.Default
    private int failRows = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PROCESSING"; // PROCESSING, SUCCESS, PARTIAL, FAILED

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void complete(int total, int success, int fail, String status, String errorDetail) {
        this.totalRows = total;
        this.successRows = success;
        this.failRows = fail;
        this.status = status;
        this.errorDetail = errorDetail;
    }
}
