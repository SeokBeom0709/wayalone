package com.example.demo.companion.dto;

import com.example.demo.companion.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationDecisionRequest(
        @NotNull(message = "수락 또는 거절을 골라 주세요.")
        ApplicationStatus status
) {
}
