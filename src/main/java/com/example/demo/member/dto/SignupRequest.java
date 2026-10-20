package com.example.demo.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = 120, message = "이메일이 너무 길어요.")
        String email,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,12}$",
                message = "닉네임은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.")
        String nickname,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "비밀번호는 영문과 숫자를 섞어 8~64자로 만들어 주세요. (공백 제외)")
        String password,

        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        String phone,

        @NotBlank(message = "휴대폰 인증을 완료해 주세요.")
        String phoneVerificationToken
) {
}
