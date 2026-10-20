package com.example.demo.manner.dto;

import com.example.demo.manner.MannerTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RatingRequest(
        @NotNull(message = "평가할 사람을 골라 주세요.")
        Long targetId,

        /** 예전 화면 호환용. 비어 있으면 참석한 것으로 봅니다. 노쇼는 NO_SHOW 태그로 남깁니다. */
        Boolean attended,

        @Min(value = 1, message = "별점은 1~5점으로 남겨 주세요.")
        @Max(value = 5, message = "별점은 1~5점으로 남겨 주세요.")
        int score,

        @Size(max = 3, message = "태그는 3개까지 고를 수 있어요.")
        List<MannerTag> tags
) {
}
