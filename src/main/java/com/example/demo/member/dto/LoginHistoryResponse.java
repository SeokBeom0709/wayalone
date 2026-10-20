package com.example.demo.member.dto;

import com.example.demo.member.LoginHistory;
import com.example.demo.member.LoginResult;

import java.time.Instant;

public record LoginHistoryResponse(LoginResult result, String ip, String userAgent, Instant createdAt) {

    public static LoginHistoryResponse from(LoginHistory history) {
        return new LoginHistoryResponse(history.getResult(), history.getIp(), history.getUserAgent(),
                history.getCreatedAt());
    }
}
