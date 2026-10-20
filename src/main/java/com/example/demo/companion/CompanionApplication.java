package com.example.demo.companion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class CompanionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private CompanionPost post;

    /** 신청한 회원 (예전 샘플 데이터는 비어 있음) */
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 500)
    private String message;

    /** 상태 컬럼이 생기기 전에 만들어진 신청은 비어 있으므로 status() 로 읽습니다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    private Instant createdAt = Instant.now();

    public ApplicationStatus status() {
        return status == null ? ApplicationStatus.PENDING : status;
    }
}
