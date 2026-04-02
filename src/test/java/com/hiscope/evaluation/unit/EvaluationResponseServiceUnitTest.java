package com.hiscope.evaluation.unit;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.dto.EvaluationSubmitRequest;
import com.hiscope.evaluation.domain.evaluation.response.entity.EvaluationResponse;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.response.service.EvaluationResponseService;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.entity.EvaluationQuestion;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationResponseServiceUnitTest {

    @Mock
    private EvaluationAssignmentRepository assignmentRepository;
    @Mock
    private EvaluationResponseRepository responseRepository;
    @Mock
    private EvaluationResponseItemRepository itemRepository;
    @Mock
    private EvaluationSessionRepository sessionRepository;
    @Mock
    private EvaluationQuestionRepository questionRepository;

    @InjectMocks
    private EvaluationResponseService evaluationResponseService;

    @Test
    void 허용되지_않은_재제출은_차단된다() {
        EvaluationAssignment assignment = EvaluationAssignment.builder()
                .id(10L)
                .sessionId(20L)
                .organizationId(1L)
                .evaluatorId(101L)
                .evaluateeId(202L)
                .status("SUBMITTED")
                .build();
        EvaluationSession session = EvaluationSession.builder()
                .id(20L)
                .organizationId(1L)
                .name("test")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .allowResubmit(false)
                .templateId(1L)
                .build();

        when(assignmentRepository.findByOrganizationIdAndId(1L, 10L)).thenReturn(Optional.of(assignment));
        when(sessionRepository.findByOrganizationIdAndId(1L, 20L)).thenReturn(Optional.of(session));

        EvaluationSubmitRequest request = new EvaluationSubmitRequest();
        request.setFinalSubmit(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluationResponseService.save(101L, 1L, 10L, request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESUBMIT_NOT_ALLOWED);
        verify(questionRepository, never()).findByTemplateIdAndActiveOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void 본인에게_배정되지_않은_평가는_접근할_수_없다() {
        EvaluationAssignment assignment = EvaluationAssignment.builder()
                .id(11L)
                .sessionId(21L)
                .organizationId(1L)
                .evaluatorId(999L)
                .evaluateeId(202L)
                .status("PENDING")
                .build();
        when(assignmentRepository.findByOrganizationIdAndId(1L, 11L)).thenReturn(Optional.of(assignment));

        EvaluationSubmitRequest request = new EvaluationSubmitRequest();
        request.setFinalSubmit(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluationResponseService.save(101L, 1L, 11L, request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(sessionRepository, never()).findByOrganizationIdAndId(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void DESCRIPTIVE_미응답으로_최종제출하면_검증에_실패한다() {
        EvaluationAssignment assignment = EvaluationAssignment.builder()
                .id(12L)
                .sessionId(22L)
                .organizationId(1L)
                .evaluatorId(101L)
                .evaluateeId(202L)
                .status("PENDING")
                .build();
        EvaluationSession session = EvaluationSession.builder()
                .id(22L)
                .organizationId(1L)
                .name("descriptive-validation")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .allowResubmit(false)
                .templateId(2L)
                .build();
        EvaluationQuestion descriptiveQuestion = EvaluationQuestion.builder()
                .id(2001L)
                .templateId(2L)
                .organizationId(1L)
                .category("협업")
                .content("서술 응답")
                .questionType("DESCRIPTIVE")
                .sortOrder(1)
                .active(true)
                .build();

        when(assignmentRepository.findByOrganizationIdAndId(1L, 12L)).thenReturn(Optional.of(assignment));
        when(sessionRepository.findByOrganizationIdAndId(1L, 22L)).thenReturn(Optional.of(session));
        when(questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(2L, true))
                .thenReturn(List.of(descriptiveQuestion));

        EvaluationSubmitRequest request = new EvaluationSubmitRequest();
        request.setFinalSubmit(true);
        request.setTexts(Map.of(2001L, "   "));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evaluationResponseService.save(101L, 1L, 12L, request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(responseRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(itemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void DESCRIPTIVE_응답이_있으면_최종제출이_통과된다() {
        EvaluationAssignment assignment = EvaluationAssignment.builder()
                .id(13L)
                .sessionId(23L)
                .organizationId(1L)
                .evaluatorId(101L)
                .evaluateeId(202L)
                .status("PENDING")
                .build();
        EvaluationSession session = EvaluationSession.builder()
                .id(23L)
                .organizationId(1L)
                .name("descriptive-submit")
                .status("IN_PROGRESS")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .allowResubmit(false)
                .templateId(3L)
                .build();
        EvaluationQuestion descriptiveQuestion = EvaluationQuestion.builder()
                .id(3001L)
                .templateId(3L)
                .organizationId(1L)
                .category("협업")
                .content("서술 응답")
                .questionType("DESCRIPTIVE")
                .sortOrder(1)
                .active(true)
                .build();
        EvaluationResponse savedResponse = EvaluationResponse.builder()
                .id(9001L)
                .assignmentId(13L)
                .organizationId(1L)
                .finalSubmit(false)
                .build();

        when(assignmentRepository.findByOrganizationIdAndId(1L, 13L)).thenReturn(Optional.of(assignment));
        when(sessionRepository.findByOrganizationIdAndId(1L, 23L)).thenReturn(Optional.of(session));
        when(questionRepository.findByTemplateIdAndActiveOrderBySortOrderAsc(3L, true))
                .thenReturn(List.of(descriptiveQuestion));
        when(responseRepository.findByAssignmentId(13L)).thenReturn(Optional.empty());
        when(responseRepository.save(org.mockito.ArgumentMatchers.any(EvaluationResponse.class)))
                .thenReturn(savedResponse);
        when(itemRepository.findByResponseIdAndQuestionId(9001L, 3001L)).thenReturn(Optional.empty());

        EvaluationSubmitRequest request = new EvaluationSubmitRequest();
        request.setFinalSubmit(true);
        request.setTexts(Map.of(3001L, "서술 답변"));

        EvaluationResponse response = evaluationResponseService.save(101L, 1L, 13L, request);

        assertThat(response.isFinalSubmit()).isTrue();
        assertThat(assignment.getStatus()).isEqualTo("SUBMITTED");
        verify(itemRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
