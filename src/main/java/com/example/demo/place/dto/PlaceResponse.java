package com.example.demo.place.dto;

import com.example.demo.place.Place;
import com.example.demo.place.PlaceCategory;
import com.example.demo.place.SoloDining;

import java.util.List;

public record PlaceResponse(
        Long id,
        String name,
        PlaceCategory category,
        String area,
        String address,
        String summary,
        SoloDining soloDining,
        int soloScore,
        Double latitude,
        Double longitude,
        List<String> tags,
        double averageRating,
        long reviewCount
) {
    public static PlaceResponse from(Place place, double averageRating, long reviewCount) {
        return new PlaceResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getArea(),
                place.getAddress(),
                place.getSummary(),
                place.getSoloDining(),
                place.getSoloScore(),
                place.getLatitude(),
                place.getLongitude(),
                List.copyOf(place.getTags()),
                averageRating,
                reviewCount
        );
    }
}
