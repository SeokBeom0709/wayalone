package com.example.demo.companion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationRequest(
        @NotBlank(message = "신청 메시지를 입력해 주세요.")
        @Size(max = 500, message = "신청 메시지는 500자까지 쓸 수 있어요.")
        String message
) {
}
