package com.example.demo.place;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("""
            SELECT p FROM Place p
            WHERE (:category IS NULL OR p.category = :category)
              AND (:foodOnly = false OR p.category IN (com.example.demo.place.PlaceCategory.RESTAURANT, com.example.demo.place.PlaceCategory.CAFE))
              AND (:spotOnly = false OR p.category = com.example.demo.place.PlaceCategory.SPOT)
              AND (:area IS NULL OR LOWER(p.area) LIKE LOWER(CONCAT('%', :area, '%')))
              AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.area) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:soloDining IS NULL OR p.soloDining = :soloDining)
            ORDER BY p.soloScore DESC, p.name ASC
            """)
    List<Place> search(
            @Param("category") PlaceCategory category,
            @Param("foodOnly") boolean foodOnly,
            @Param("spotOnly") boolean spotOnly,
            @Param("area") String area,
            @Param("q") String q,
            @Param("soloDining") SoloDining soloDining
    );

    /**
     * 반경 검색. 두 단계로 걸러냅니다.
     * 1) 좌표 사각형(BETWEEN)으로 후보를 좁힙니다. (위도, 경도) 인덱스를 타는 부분입니다.
     * 2) 후보에만 Haversine 거리 계산을 해서 원 안의 장소만 남기고 가까운 순으로 자릅니다.
     * 거리 계산을 WHERE 에 바로 쓰면 모든 행에 계산이 걸려 인덱스를 쓸 수 없습니다.
     */
    @Query(value = """
            SELECT t.* FROM (
                SELECT p.*,
                       2 * 6371000 * ASIN(SQRT(
                           POWER(SIN(RADIANS(p.latitude - :lat) / 2), 2)
                         + COS(RADIANS(:lat)) * COS(RADIANS(p.latitude)) * POWER(SIN(RADIANS(p.longitude - :lng) / 2), 2)
                       )) AS distance_m
                  FROM place p
                 WHERE p.latitude BETWEEN :minLat AND :maxLat
                   AND p.longitude BETWEEN :minLng AND :maxLng
            ) t
             WHERE t.distance_m <= :radius
             ORDER BY t.distance_m
             LIMIT :limit
            """, nativeQuery = true)
    List<Place> findWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("radius") double radius,
            @Param("limit") int limit
    );
}
