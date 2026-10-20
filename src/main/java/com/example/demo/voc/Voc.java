package com.example.demo.voc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 회원이 남기는 문의·신고·오류 제보. 관리자는 답변(회원에게 보임)과 내부 메모(회원에게 안 보임)를 남깁니다. */
@Entity
@Table(name = "vocs", indexes = {
        @Index(name = "idx_voc_status", columnList = "status"),
        @Index(name = "idx_voc_target_member", columnList = "targetMemberId"),
        @Index(name = "idx_voc_reporter", columnList = "reporterId")
})
@Getter
@Setter
@NoArgsConstructor
public class Voc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VocType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private VocStatus status = VocStatus.OPEN;

    @Column(nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 20)
    private String reporterNickname;

    private Long targetMemberId;

    private Long targetPostId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 1000)
    private String answer;

    @Column(length = 1000)
    private String adminMemo;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private Instant resolvedAt;
}
