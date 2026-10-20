package com.example.demo.companion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CompanionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 30)
    private String destination;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private int recruitCount;

    /** 작성한 회원 (예전 샘플 데이터는 비어 있음) */
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 2000, nullable = false)
    private String content;

    @Column(length = 40)
    private String style;

    /** 관리자가 숨긴 글. 목록과 상세에서 빠지고, 작성자의 '내 모집글'에는 숨김 표시로 남습니다. */
    private Boolean hidden;

    private Instant createdAt = Instant.now();

    /** 작성자가 지우거나 작성자가 탈퇴해서 삭제 처리된 시각. 목록과 상세에서 완전히 빠집니다. (기록은 content_deletion_logs) */
    private Instant deletedAt;

    public boolean hiddenOrDefault() {
        return Boolean.TRUE.equals(hidden);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 삭제 시점 내용을 기록용 문자열로 줄입니다. */
    public String snapshotText() {
        String text = "[%s] %s · %s ~ %s · 모집 %d명 · %s".formatted(
                destination, title, startDate, endDate, recruitCount, content);
        return text.length() > 2400 ? text.substring(0, 2400) : text;
    }
}
