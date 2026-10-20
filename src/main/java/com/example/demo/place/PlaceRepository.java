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
}
