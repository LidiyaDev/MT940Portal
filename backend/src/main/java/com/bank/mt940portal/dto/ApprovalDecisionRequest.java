package com.bank.mt940portal.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApprovalDecisionRequest {

    @Size(max = 1000)
    private String note;
}
