package com.example.demo.member.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneAttachRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        String phone,

        @NotBlank(message = "휴대폰 인증을 완료해 주세요.")
        String phoneVerificationToken
) {
}
