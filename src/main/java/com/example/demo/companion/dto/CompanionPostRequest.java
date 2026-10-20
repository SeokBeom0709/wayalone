package com.example.demo.companion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CompanionPostRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자까지 쓸 수 있어요.")
        String title,

        @NotBlank(message = "여행지를 입력해 주세요.")
        @Size(max = 30, message = "여행지는 30자까지 쓸 수 있어요.")
        String destination,

        @NotNull(message = "시작일을 선택해 주세요.") LocalDate startDate,
        @NotNull(message = "종료일을 선택해 주세요.") LocalDate endDate,

        @Min(value = 1, message = "모집 인원은 1~10명이에요.")
        @Max(value = 10, message = "모집 인원은 1~10명이에요.")
        int recruitCount,

        @NotBlank(message = "소개를 입력해 주세요.")
        @Size(max = 2000, message = "소개는 2000자까지 쓸 수 있어요.")
        String content,

        @Size(max = 40, message = "스타일은 40자까지 쓸 수 있어요.")
        String style
) {
}
