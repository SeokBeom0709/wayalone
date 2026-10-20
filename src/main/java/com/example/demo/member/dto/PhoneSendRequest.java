package com.example.demo.member.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneSendRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        String phone
) {
}
