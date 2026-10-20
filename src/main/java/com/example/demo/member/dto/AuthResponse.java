package com.example.demo.member.dto;

public record AuthResponse(String token, MemberResponse member) {
}
