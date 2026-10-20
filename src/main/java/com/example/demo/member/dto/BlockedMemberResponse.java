package com.example.demo.member.dto;

import java.time.Instant;

public record BlockedMemberResponse(Long memberId, String nickname, Instant blockedAt) {
}
