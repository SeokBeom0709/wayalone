package com.example.demo.place;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
// 좌표 범위 조회(반경 검색의 1차 필터)가 인덱스를 타도록 (위도, 경도) 복합 인덱스를 둡니다.
@Table(name = "place", indexes = @Index(name = "idx_place_lat_lng", columnList = "latitude, longitude"))
@Getter
@Setter
@NoArgsConstructor
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceCategory category;

    @Column(nullable = false)
    private String area;

    private String address;

    @Column(length = 1000)
    private String summary;

    @Enumerated(EnumType.STRING)
    private SoloDining soloDining = SoloDining.UNKNOWN;

    private int soloScore;

    private Double latitude;

    private Double longitude;

    // 장소마다 태그를 따로 조회하지 않고 최대 100개 장소의 태그를 한 번에 가져옵니다.
    @BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "place_tags", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();
}
