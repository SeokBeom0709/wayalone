package com.example.demo.manner.dto;

public record MemberProfileResponse(Long id, String nickname, boolean phoneVerified, MannerSummary manner) {
}
