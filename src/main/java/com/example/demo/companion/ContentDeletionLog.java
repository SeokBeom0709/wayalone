package com.example.demo.companion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 삭제된 콘텐츠의 기록. 분쟁이나 신고가 들어왔을 때 무엇이 있었는지 확인할 수 있도록
 * 삭제 시점의 내용을 요약해서 남깁니다.
 */
@Entity
@Table(name = "content_deletion_logs", indexes = @Index(name = "idx_deletion_target", columnList = "targetType, targetId"))
@Getter
@Setter
@NoArgsConstructor
public class ContentDeletionLog {

    public static final String BY_AUTHOR = "AUTHOR";
    public static final String BY_WITHDRAWAL = "WITHDRAWAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    /** 지운 사람(회원 id) */
    private Long actorId;

    /** AUTHOR(작성자가 직접), WITHDRAWAL(탈퇴하면서) */
    @Column(nullable = false, length = 20)
    private String reason;

    /** 삭제 시점의 제목·장소·일정·본문 요약 */
    @Column(length = 2500)
    private String snapshot;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
