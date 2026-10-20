package com.example.demo.voc.dto;

import com.example.demo.voc.VocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VocRequest(
        @NotNull(message = "문의 종류를 골라 주세요.")
        VocType type,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 1000, message = "내용은 1000자까지 쓸 수 있어요.")
        String content,

        Long targetMemberId,

        Long targetPostId
) {
}
