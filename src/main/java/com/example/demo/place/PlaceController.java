package com.example.demo.place;

import com.example.demo.place.dto.LocationResponse;
import com.example.demo.place.dto.NearbyResponse;
import com.example.demo.place.dto.PlaceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;
    private final KakaoLocalClient kakaoLocalClient;

    public PlaceController(PlaceService placeService, KakaoLocalClient kakaoLocalClient) {
        this.placeService = placeService;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    @GetMapping
    public List<PlaceResponse> search(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SoloDining soloDining,
            @RequestParam(required = false) PlaceCategory category
    ) {
        return placeService.search(intent, area, q, soloDining, category);
    }

    @GetMapping("/nearby")
    public NearbyResponse nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radius,
            @RequestParam(defaultValue = "1") int page
    ) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "위치 좌표가 올바르지 않아요.");
        }
        int safeRadius = Math.max(100, Math.min(radius, 20000));
        return kakaoLocalClient.nearbyRestaurants(lat, lng, safeRadius, Math.max(page, 1));
    }

    @GetMapping("/locations")
    public List<LocationResponse> locations(@RequestParam String q) {
        String query = q.trim();
        if (query.isEmpty() || query.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "찾을 위치를 1~50자로 입력해 주세요.");
        }
        return kakaoLocalClient.searchLocations(query);
    }

    @GetMapping("/{id}")
    public PlaceResponse get(@PathVariable Long id) {
        return placeService.get(id);
    }
}
