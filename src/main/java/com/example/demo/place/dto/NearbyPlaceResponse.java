package com.example.demo.place.dto;

public record NearbyPlaceResponse(
        String kakaoId,
        String name,
        String category,
        String address,
        String phone,
        int distance,
        double latitude,
        double longitude,
        String kakaoUrl
) {
}
