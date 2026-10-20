package com.example.demo.admin;

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
 * 관리자가 한 일(제재, 게시글 숨김, VOC 처리)을 남기는 감사 로그. 기록은 수정·삭제하지 않고,
 * 잘못 처리한 건은 처리 직전 상태(prev*)로 복구하는 REVERT 기록을 새로 남깁니다.
 */
@Entity
@Table(name = "admin_actions", indexes = @Index(name = "idx_admin_action_target", columnList = "targetType,targetId"))
@Getter
@Setter
@NoArgsConstructor
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 20)
    private String adminNickname;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 10)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String detail;

    /*
     * 처리 직전 상태. 대상별 의미:
     *   MEMBER: prevStatus=회원 상태, prevUntil=정지 만료, prevText1=제재 사유
     *   POST:   prevFlag=숨김 여부
     *   VOC:    prevStatus=VOC 상태, prevUntil=완료 시각, prevText1=답변, prevText2=내부 메모
     */
    @Column(length = 20)
    private String prevStatus;

    private Instant prevUntil;

    @Column(length = 1000)
    private String prevText1;

    @Column(length = 1000)
    private String prevText2;

    private Boolean prevFlag;

    /** 이 기록이 REVERT 일 때 되돌린 원래 처리의 id */
    private Long revertOfId;

    private Instant revertedAt;

    private Long revertedById;

    @Column(length = 20)
    private String revertedByNickname;

    @Column(nullable = false)
    private Instant createdAt;

    public boolean isRevert() {
        return "REVERT".equals(action);
    }

    public boolean hasSnapshot() {
        return "POST".equals(targetType) ? prevFlag != null : prevStatus != null;
    }
}
