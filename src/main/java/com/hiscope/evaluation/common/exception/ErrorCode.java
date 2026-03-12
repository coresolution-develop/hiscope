package com.hiscope.evaluation.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),

    // 기관
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "기관을 찾을 수 없습니다."),
    ORGANIZATION_CODE_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 기관 코드입니다."),

    // 계정
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다."),
    LOGIN_ID_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),

    // 부서
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."),
    DEPARTMENT_CODE_DUPLICATE(HttpStatus.CONFLICT, "같은 기관 내에 이미 사용 중인 부서 코드입니다."),
    DEPARTMENT_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 부서가 존재하여 삭제할 수 없습니다."),
    DEPARTMENT_HAS_EMPLOYEES(HttpStatus.CONFLICT, "직원이 배정된 부서는 삭제할 수 없습니다."),

    // 직원
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."),
    EMPLOYEE_NUMBER_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 사원번호입니다."),

    // 평가 템플릿
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 템플릿을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 문항을 찾을 수 없습니다."),

    // 평가 세션
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 세션을 찾을 수 없습니다."),
    SESSION_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 평가 세션입니다."),
    SESSION_NOT_IN_PROGRESS(HttpStatus.BAD_REQUEST, "진행 중인 평가 세션이 아닙니다."),
    SESSION_CLOSED(HttpStatus.BAD_REQUEST, "종료된 평가 세션입니다."),
    SESSION_STATUS_INVALID(HttpStatus.BAD_REQUEST, "세션 상태 전환이 불가합니다."),

    // 평가 관계
    RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 관계를 찾을 수 없습니다."),
    RELATIONSHIP_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 평가 관계입니다."),
    SELF_EVALUATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신을 평가할 수 없습니다."),

    // 평가 배정
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 배정을 찾을 수 없습니다."),
    ASSIGNMENT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출된 평가입니다."),
    RESUBMIT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "재제출이 허용되지 않는 평가입니다."),

    // 엑셀
    EXCEL_PARSE_ERROR(HttpStatus.BAD_REQUEST, "엑셀 파일 파싱 중 오류가 발생했습니다."),
    EXCEL_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. .xlsx 파일만 업로드 가능합니다."),
    EXCEL_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "다운로드 템플릿 파일을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
