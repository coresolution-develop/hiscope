package com.hiscope.evaluation.unit;

import com.hiscope.evaluation.common.exception.BusinessException;
import com.hiscope.evaluation.common.exception.ErrorCode;
import com.hiscope.evaluation.domain.evaluation.assignment.entity.EvaluationAssignment;
import com.hiscope.evaluation.domain.evaluation.assignment.repository.EvaluationAssignmentRepository;
import com.hiscope.evaluation.domain.evaluation.response.dto.EvaluationSubmitRequest;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseItemRepository;
import com.hiscope.evaluation.domain.evaluation.response.repository.EvaluationResponseRepository;
import com.hiscope.evaluation.domain.evaluation.response.service.EvaluationResponseService;
import com.hiscope.evaluation.domain.evaluation.session.entity.EvaluationSession;
import com.hiscope.evaluation.domain.evaluation.session.repository.EvaluationSessionRepository;
import com.hiscope.evaluation.domain.evaluation.template.repository.EvaluationQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
}
