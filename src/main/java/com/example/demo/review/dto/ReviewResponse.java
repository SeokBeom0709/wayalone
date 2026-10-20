package com.example.demo.review.dto;

import com.example.demo.review.SoloReview;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        String nickname,
        int rating,
        String content,
        boolean wentAlone,
        Instant createdAt
) {
    public static ReviewResponse from(SoloReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getNickname(),
                review.getRating(),
                review.getContent(),
                review.isWentAlone(),
                review.getCreatedAt()
        );
    }
}
