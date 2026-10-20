package com.example.demo.manner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** 같은 동행 일정에 함께한 회원끼리 남기는 매너 평가. 한 일정에서 같은 상대에게는 한 번만 남길 수 있습니다. */
@Entity
@Table(name = "manner_ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_manner_rating", columnNames = {"postId", "raterId", "targetId"}),
        indexes = @Index(name = "idx_manner_rating_target", columnList = "targetId"))
@Getter
@Setter
@NoArgsConstructor
public class MannerRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long raterId;

    @Column(nullable = false)
    private Long targetId;

    private int score;

    /** 약속 장소에 나왔는지. 이 컬럼이 생기기 전 평가는 비어 있으므로 attendedOrDefault() 로 읽습니다. */
    private Boolean attended;

    /** MannerTag 이름을 쉼표로 이어 저장합니다. */
    @Column(length = 200)
    private String tags;

    @Column(nullable = false)
    private Instant createdAt;

    public boolean attendedOrDefault() {
        return attended == null || attended;
    }

    public List<MannerTag> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(MannerTag::valueOf).toList();
    }
}
