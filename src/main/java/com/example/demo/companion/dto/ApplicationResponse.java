package com.example.demo.companion.dto;

import com.example.demo.companion.ApplicationStatus;
import com.example.demo.companion.CompanionApplication;
import com.example.demo.manner.dto.MannerSummary;

import java.time.Instant;

public record ApplicationResponse(
        Long id,
        Long memberId,
        String nickname,
        String message,
        ApplicationStatus status,
        boolean phoneVerified,
        MannerSummary manner,
        Instant createdAt
) {
    public static ApplicationResponse from(CompanionApplication application, boolean phoneVerified, MannerSummary manner) {
        return new ApplicationResponse(
                application.getId(),
                application.getMemberId(),
                application.getNickname(),
                application.getMessage(),
                application.status(),
                phoneVerified,
                manner,
                application.getCreatedAt()
        );
    }
}
