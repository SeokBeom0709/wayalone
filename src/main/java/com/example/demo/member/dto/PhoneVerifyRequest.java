package com.example.demo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerifyRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        String phone,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호 6자리를 입력해 주세요.")
        String code
) {
}
