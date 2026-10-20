package com.example.demo.companion.dto;

import com.example.demo.companion.CompanionPost;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record CompanionPostResponse(
        Long id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        long nights,
        int recruitCount,
        long applicantCount,
        long acceptedCount,
        Long memberId,
        String nickname,
        String content,
        String style,
        String status,
        boolean hidden,
        Instant createdAt
) {
    public static CompanionPostResponse from(CompanionPost post, long applicantCount, long acceptedCount) {
        boolean ended = post.getEndDate().isBefore(LocalDate.now());
        return new CompanionPostResponse(
                post.getId(),
                post.getTitle(),
                post.getDestination(),
                post.getStartDate(),
                post.getEndDate(),
                ChronoUnit.DAYS.between(post.getStartDate(), post.getEndDate()),
                post.getRecruitCount(),
                applicantCount,
                acceptedCount,
                post.getMemberId(),
                post.getNickname(),
                post.getContent(),
                post.getStyle(),
                ended ? "ENDED" : "OPEN",
                post.hiddenOrDefault(),
                post.getCreatedAt()
        );
    }
}
