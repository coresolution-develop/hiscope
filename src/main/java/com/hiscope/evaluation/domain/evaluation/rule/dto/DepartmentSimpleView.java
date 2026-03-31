package com.hiscope.evaluation.domain.evaluation.rule.dto;

public record DepartmentSimpleView(
        Long id,
        String name,
        int totalEmployees,
        int leaderCount
) {}
