package com.example.demo.voc.dto;

import com.example.demo.voc.Voc;
import com.example.demo.voc.VocStatus;
import com.example.demo.voc.VocType;

import java.time.Instant;

/** adminMemo 는 관리자 화면에서만 채워지고, 회원에게는 항상 null 입니다. */
public record VocResponse(
        Long id,
        VocType type,
        VocStatus status,
        Long reporterId,
        String reporterNickname,
        Long targetMemberId,
        String targetMemberNickname,
        Long targetPostId,
        String content,
        String answer,
        String adminMemo,
        Instant createdAt,
        Instant updatedAt
) {
    public static VocResponse forMember(Voc voc, String targetNickname) {
        return of(voc, targetNickname, null);
    }

    public static VocResponse forAdmin(Voc voc, String targetNickname) {
        return of(voc, targetNickname, voc.getAdminMemo());
    }

    private static VocResponse of(Voc voc, String targetNickname, String adminMemo) {
        return new VocResponse(voc.getId(), voc.getType(), voc.getStatus(), voc.getReporterId(),
                voc.getReporterNickname(), voc.getTargetMemberId(), targetNickname, voc.getTargetPostId(),
                voc.getContent(), voc.getAnswer(), adminMemo, voc.getCreatedAt(), voc.getUpdatedAt());
    }
}
