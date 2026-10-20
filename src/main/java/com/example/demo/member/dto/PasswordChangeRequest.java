package com.example.demo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "비밀번호는 영문과 숫자를 섞어 8~64자로 만들어 주세요. (공백 제외)")
        String newPassword
) {
}
