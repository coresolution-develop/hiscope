package com.hiscope.evaluation.domain.evaluation.template.service;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.common.security.SecurityUtils;
import com.hiscope.evaluation.domain.evaluation.template.dto.QuestionRequest;
import com.hiscope.evaluation.domain.evaluation.template.dto.TemplateRequest;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationTemplate;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationTemplateService {

    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationQuestionRepository questionRepository;

    public List<EvaluationTemplate> findAll(Long orgId) {
        return findAll(orgId, null, null);
    }

    public List<EvaluationTemplate> findAll(Long orgId, String keyword, Boolean active) {
        SecurityUtils.checkOrgAccess(orgId);
        return templateRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(t -> active == null || t.isActive() == active)
                .filter(t -> {
                    if (!StringUtils.hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase();
                    return t.getName().toLowerCase().contains(normalized)
                            || (t.getDescription() != null && t.getDescription().toLowerCase().contains(normalized));
                })
                .toList();
    }

    public Page<EvaluationTemplate> searchPage(Long orgId, String keyword, Boolean active, Pageable pageable) {
        SecurityUtils.checkOrgAccess(orgId);
        String normalizedKeyword = normalizeKeyword(keyword);
        Specification<EvaluationTemplate> spec =
                Specification.where((root, query, cb) -> cb.equal(root.get("organizationId"), orgId));
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (StringUtils.hasText(normalizedKeyword)) {
            String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), likeKeyword),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), likeKeyword)
            ));
        }
        return templateRepository.findAll(spec, pageable);
    }

    public EvaluationTemplate findById(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        return getByOrgAndId(orgId, id);
    }

    public List<EvaluationQuestion> findQuestions(Long orgId, Long templateId) {
        SecurityUtils.checkOrgAccess(orgId);
        getByOrgAndId(orgId, templateId); // 소유권 검증
        return questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(templateId, true);
    }

    @Transactional
    public EvaluationTemplate createTemplate(Long orgId, TemplateRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        return templateRepository.save(toTemplateEntity(orgId, request));
    }

    @Transactional
    public EvaluationTemplate updateTemplate(Long orgId, Long id, TemplateRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationTemplate template = getByOrgAndId(orgId, id);
        template.update(request.getName(), request.getDescription());
        return template;
    }

    @Transactional
    public void deleteTemplate(Long orgId, Long id) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationTemplate template = getByOrgAndId(orgId, id);
        template.deactivate();
    }

    @Transactional
    public EvaluationQuestion addQuestion(Long orgId, Long templateId, QuestionRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        getByOrgAndId(orgId, templateId);
        return questionRepository.save(toQuestionEntity(orgId, templateId, request));
    }

    @Transactional
    public EvaluationQuestion updateQuestion(Long orgId, Long questionId, QuestionRequest request) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationQuestion q = getQuestionByOrgAndId(orgId, questionId);
        q.update(request.getCategory(), request.getContent(), request.getQuestionType(), null,
                request.getMaxScore(), request.getSortOrder());
        return q;
    }

    @Transactional
    public void deleteQuestion(Long orgId, Long questionId) {
        SecurityUtils.checkOrgAccess(orgId);
        EvaluationQuestion q = getQuestionByOrgAndId(orgId, questionId);
        q.deactivate();
    }

    public EvaluationTemplate getByOrgAndId(Long orgId, Long id) {
        return templateRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
    }

    private EvaluationQuestion getQuestionByOrgAndId(Long orgId, Long questionId) {
        return questionRepository.findByOrganizationIdAndId(orgId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
    }

    private EvaluationTemplate toTemplateEntity(Long orgId, TemplateRequest request) {
        return EvaluationTemplate.builder()
                .organizationId(orgId)
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();
    }

    private EvaluationQuestion toQuestionEntity(Long orgId, Long templateId, QuestionRequest request) {
        return EvaluationQuestion.builder()
                .templateId(templateId)
                .organizationId(orgId)
                .category(request.getCategory())
                .content(request.getContent())
                .questionType(request.getQuestionType())
                .questionGroupCode(null)
                .maxScore(request.getMaxScore())
                .sortOrder(request.getSortOrder())
                .active(true)
                .build();
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}
