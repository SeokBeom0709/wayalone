package com.example.demo.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @Min(value = 1, message = "별점은 1~5점으로 남겨 주세요.")
        @Max(value = 5, message = "별점은 1~5점으로 남겨 주세요.")
        int rating,

        @NotBlank(message = "후기 내용을 입력해 주세요.")
        @Size(max = 1000, message = "후기는 1000자까지 쓸 수 있어요.")
        String content,

        Boolean wentAlone
) {
    public boolean wentAloneOrDefault() {
        return wentAlone == null || wentAlone;
    }
}
