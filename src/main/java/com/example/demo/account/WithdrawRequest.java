package com.example.demo.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
        @Size(max = 200, message = "탈퇴 사유는 200자까지 쓸 수 있어요.") String reason
) {
}
