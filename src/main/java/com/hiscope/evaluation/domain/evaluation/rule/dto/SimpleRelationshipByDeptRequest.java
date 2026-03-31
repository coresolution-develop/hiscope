package com.hiscope.evaluation.domain.evaluation.rule.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SimpleRelationshipByDeptRequest {

    private boolean upwardEnabled;
    private List<Long> upwardDeptIds = new ArrayList<>();

    private boolean peerEnabled;
    private List<Long> peerDeptIds = new ArrayList<>();

    private boolean downwardEnabled;
    private List<Long> downwardDeptIds = new ArrayList<>();
}
