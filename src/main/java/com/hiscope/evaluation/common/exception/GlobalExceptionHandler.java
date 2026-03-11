package com.hiscope.evaluation.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 업무 예외 (BusinessException)
     * 컨트롤러 레이어에서 먼저 catch 하지 않은 경우 이곳에서 처리.
     * 평가 저장/제출 등 POST redirect 흐름에서는 컨트롤러가 직접 catch하여 flash 메시지로 처리하므로,
     * 여기서 처리되는 경우는 주로 GET 요청 또는 예기치 않은 흐름.
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest req, Model model) {
        log.warn("BusinessException [{}] {} {}: {}",
                req.getMethod(), req.getRequestURI(),
                e.getErrorCode().name(), e.getMessage());
        return buildErrorResponse(req, model, e.getErrorCode().getHttpStatus().value(),
                e.getErrorCode().name(), e.getMessage());
    }

    /** 폼 바인딩 유효성 오류 */
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e, HttpServletRequest req, Model model) {
        log.warn("BindException: {}", e.getMessage());
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("입력값 검증 실패");
        return buildErrorResponse(req, model, 400, "BIND_ERROR", message);
    }

    /**
     * URL 파라미터 타입 불일치 (예: /evaluations/abc → Long 변환 실패)
     * 운영 중 발생 시 공격 시도 가능성이 있어 warn 레벨 로깅
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                     HttpServletRequest req, Model model) {
        log.warn("MethodArgumentTypeMismatch [{}] {}: param='{}' value='{}'",
                req.getMethod(), req.getRequestURI(), e.getName(), e.getValue());
        return buildErrorResponse(req, model, 400, "TYPE_MISMATCH", "요청 파라미터 형식이 올바르지 않습니다.");
    }

    /**
     * 파일 업로드 크기 초과
     * spring.servlet.multipart.max-file-size / max-request-size 설정값 초과 시 발생
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest req, Model model) {
        log.warn("MaxUploadSizeExceeded: {}", e.getMessage());
        return buildErrorResponse(req, model, 400, "UPLOAD_SIZE_EXCEEDED",
                "파일 크기가 너무 큽니다. xlsx 파일은 10MB 이하로 업로드해주세요.");
    }

    /**
     * Spring Security 접근 거부
     * FORBIDDEN(403) — 로그인은 됐으나 해당 URL에 대한 역할이 없을 때
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Object handleAccessDenied(HttpServletRequest req, Model model) {
        log.warn("AccessDenied [{}] {} user={}",
                req.getMethod(), req.getRequestURI(),
                req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "anonymous");
        return buildErrorResponse(req, model, 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    /**
     * 그 외 처리되지 않은 예외 — 운영자 확인이 필요한 수준이므로 error 레벨 로깅
     * 스택 트레이스 전체를 로그에 남겨 원인 추적 가능하도록 유지
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest req, Model model) {
        log.error("Unhandled exception [{}] {}", req.getMethod(), req.getRequestURI(), e);
        return buildErrorResponse(req, model, 500, "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    private Object buildErrorResponse(HttpServletRequest req, Model model,
                                      int status, String code, String message) {
        String requestId = MDC.get("requestId");
        if (expectsJson(req)) {
            return ResponseEntity.status(status).body(ErrorResponse.builder()
                    .timestamp(java.time.LocalDateTime.now())
                    .status(status)
                    .code(code)
                    .message(message)
                    .path(req.getRequestURI())
                    .requestId(requestId)
                    .build());
        }
        model.addAttribute("errorCode", code);
        model.addAttribute("errorMessage", message);
        model.addAttribute("httpStatus", status);
        model.addAttribute("requestId", requestId);
        return "error/error";
    }

    private boolean expectsJson(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String requestedWith = req.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return true;
        }
        return req.getRequestURI() != null && req.getRequestURI().startsWith("/api/");
    }
}
