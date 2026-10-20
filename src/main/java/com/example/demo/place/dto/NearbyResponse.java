package com.example.demo.place.dto;

import java.util.List;

public record NearbyResponse(List<NearbyPlaceResponse> places, boolean end) {
}
