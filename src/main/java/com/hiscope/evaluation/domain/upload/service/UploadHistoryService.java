package com.hiscope.evaluation.domain.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiscope.evaluation.domain.upload.dto.UploadResult;
import com.hiscope.evaluation.domain.upload.entity.UploadHistory;
import com.hiscope.evaluation.domain.upload.repository.UploadHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 업로드 이력 전담 서비스.
 *
 * 왜 별도 빈으로 분리했는가:
 * Spring @Transactional은 프록시 기반이므로 같은 클래스 내 자기 호출(self-invocation)에서는
 * 전파 속성이 무시된다. UploadService.saveHistory()에 @Transactional(REQUIRES_NEW)를 붙여도
 * 실제로는 동작하지 않는다.
 *
 * 이 빈을 별도로 분리하면:
 * - record() 가 REQUIRES_NEW 트랜잭션에서 독립적으로 커밋됨
 * - 메인 업로드 트랜잭션이 롤백(파일 파싱 오류 등)돼도 이력은 항상 저장됨
 * - 운영자가 실패한 업로드 시도를 추적 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadHistoryService {

    private final UploadHistoryRepository uploadHistoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * 업로드 이력 저장 — 항상 독립 트랜잭션으로 커밋.
     * 메인 업로드 트랜잭션의 성공/실패와 무관하게 이력은 반드시 기록된다.
     *
     * @param orgId      기관 ID
     * @param result     업로드 결과 (SUCCESS / PARTIAL / FAILED)
     * @param uploadedBy 업로드 수행 사용자 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long orgId, UploadResult result, Long uploadedBy) {
        String errorJson = null;
        if (result.hasErrors()) {
            try {
                errorJson = objectMapper.writeValueAsString(result.getErrors());
            } catch (JsonProcessingException e) {
                // JSON 직렬화 실패 시 에러 상세는 생략하고 이력 자체는 저장
                log.warn("업로드 오류 목록 JSON 직렬화 실패 (org={}, type={}): {}",
                        orgId, result.getUploadType(), e.getMessage());
            }
        }
        uploadHistoryRepository.save(UploadHistory.builder()
                .organizationId(orgId)
                .uploadType(result.getUploadType())
                .fileName(result.getFileName() != null ? result.getFileName() : "unknown")
                .totalRows(result.getTotalRows())
                .successRows(result.getSuccessRows())
                .failRows(result.getFailRows())
                .status(result.getStatus())
                .errorDetail(errorJson)
                .uploadedBy(uploadedBy)
                .build());
    }

    /** 기관의 업로드 이력 목록 조회 */
    @Transactional(readOnly = true)
    public List<UploadHistory> findByOrg(Long orgId) {
        return uploadHistoryRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    /** 운영 화면용 최근 N건 조회 */
    @Transactional(readOnly = true)
    public List<UploadHistory> findRecentByOrg(Long orgId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        return uploadHistoryRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, PageRequest.of(0, safeLimit));
    }
}
