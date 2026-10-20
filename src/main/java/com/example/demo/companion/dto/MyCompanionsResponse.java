package com.example.demo.companion.dto;

import com.example.demo.companion.ApplicationStatus;

import java.util.List;

public record MyCompanionsResponse(List<CompanionPostResponse> posts, List<MyApplication> applications) {

    public record MyApplication(Long applicationId, ApplicationStatus status, CompanionPostResponse post) {
    }
}
