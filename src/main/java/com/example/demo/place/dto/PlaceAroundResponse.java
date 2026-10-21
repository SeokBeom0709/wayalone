package com.example.demo.place.dto;

/** 반경 검색 결과: 장소와 기준 위치로부터의 거리(m) */
public record PlaceAroundResponse(PlaceResponse place, int distanceMeters) {
}
