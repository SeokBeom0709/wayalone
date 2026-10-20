package com.example.demo.review;

import com.example.demo.place.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SoloReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    /** 작성한 회원 (예전 샘플 데이터는 비어 있음) */
    private Long memberId;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000, nullable = false)
    private String content;

    private boolean wentAlone = true;

    private Instant createdAt = Instant.now();
}
