package com.example.demo.member.dto;

import com.example.demo.member.Member;
import com.example.demo.member.MemberRole;

public record MemberResponse(Long id, String email, String nickname, boolean phoneVerified, MemberRole role) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(),
                member.isPhoneVerified(), member.getRole() == null ? MemberRole.USER : member.getRole());
    }
}
