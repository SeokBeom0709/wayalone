package com.example.demo.admin;

import com.example.demo.companion.ApplicationStatus;
import com.example.demo.companion.ContentDeletionLog;
import com.example.demo.manner.dto.MannerSummary;
import com.example.demo.member.LoginHistory;
import com.example.demo.member.LoginResult;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRole;
import com.example.demo.member.MemberStatus;
import com.example.demo.member.MemberWithdrawal;
import com.example.demo.member.PhoneBlacklist;
import com.example.demo.voc.VocStatus;
import com.example.demo.voc.dto.VocResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자 API 요청/응답 모음 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public enum SanctionAction { SUSPEND, BAN, RELEASE }

    public record Stats(
            long members, long newMembersToday, long newMembersWeek, long phoneVerified,
            long suspended, long banned, long withdrawn, long blacklistedPhones,
            long posts, long hiddenPosts, long applications,
            long ratings, long noShows,
            long openVocs, long inProgressVocs, long resolvedVocs,
            List<DailyPoint> daily
    ) {
    }

    /** 하루(한국 시간 기준)에 새로 생긴 수 */
    public record DailyPoint(LocalDate date, long members, long posts, long applications, long vocs) {
    }

    public record MemberRow(
            Long id, String email, String nickname, MemberRole role, MemberStatus status,
            Instant suspendedUntil, String statusReason, boolean phoneVerified, boolean blocked,
            Instant createdAt, MannerSummary manner, long reportCount
    ) {
        static MemberRow of(Member m, MannerSummary manner, long reportCount) {
            return new MemberRow(m.getId(), m.getEmail(), m.getNickname(), m.getRole(), m.getStatus(),
                    m.getSuspendedUntil(), m.getStatusReason(), m.isPhoneVerified(), m.isBlocked(Instant.now()),
                    m.getCreatedAt(), manner, reportCount);
        }
    }

    public record MemberPage(List<MemberRow> members, int page, int totalPages, long total) {
    }

    public record PostRow(Long id, String title, String destination, LocalDate startDate, boolean hidden,
                          boolean deleted, long applicantCount, Instant createdAt) {
    }

    /** 로그인 시도 기록 한 줄 */
    public record LoginRow(LoginResult result, String ip, String userAgent, Instant createdAt) {
        static LoginRow of(LoginHistory h) {
            return new LoginRow(h.getResult(), h.getIp(), h.getUserAgent(), h.getCreatedAt());
        }
    }

    /** 탈퇴 기록 (개인정보 없음) */
    public record WithdrawalInfo(Instant withdrawnAt, String reason, int deletedPosts, int canceledApplications) {
        static WithdrawalInfo of(MemberWithdrawal w) {
            return new WithdrawalInfo(w.getWithdrawnAt(), w.getReason(), w.getDeletedPosts(),
                    w.getCanceledApplications());
        }
    }

    /** 차단된 번호 목록의 한 줄. 해시는 앞 8자리만 보여 줍니다. */
    public record BlacklistRow(Long id, String phoneHashPrefix, String reason, Long sourceMemberId,
                               Instant createdAt) {
        static BlacklistRow of(PhoneBlacklist b) {
            return new BlacklistRow(b.getId(), b.getPhoneHash().substring(0, 8), b.getReason(),
                    b.getSourceMemberId(), b.getCreatedAt());
        }
    }

    /** 삭제 기록 한 줄 */
    public record DeletionRow(Long id, String targetType, Long targetId, Long actorId, String reason,
                              String snapshot, Instant createdAt) {
        static DeletionRow of(ContentDeletionLog l) {
            return new DeletionRow(l.getId(), l.getTargetType(), l.getTargetId(), l.getActorId(), l.getReason(),
                    l.getSnapshot(), l.getCreatedAt());
        }
    }

    public record ApplicationRow(Long id, Long postId, String postTitle, ApplicationStatus status,
                                 String message, Instant createdAt) {
    }

    public record RatingRow(Long postId, Long raterId, String raterNickname, int score, boolean attended,
                            List<String> tags, Instant createdAt) {
    }

    public record ActionRow(Long id, String adminNickname, String action, String targetType, Long targetId,
                            String detail, Instant createdAt,
                            Long revertOfId, Instant revertedAt, String revertedByNickname, boolean revertible) {
        static ActionRow of(AdminAction a, boolean revertible) {
            return new ActionRow(a.getId(), a.getAdminNickname(), a.getAction(), a.getTargetType(),
                    a.getTargetId(), a.getDetail(), a.getCreatedAt(),
                    a.getRevertOfId(), a.getRevertedAt(), a.getRevertedByNickname(), revertible);
        }
    }

    public record RevertRequest(
            @NotBlank(message = "되돌리는 이유를 입력해 주세요.")
            @Size(max = 200, message = "사유는 200자까지 쓸 수 있어요.") String reason
    ) {
    }

    public record MemberDetail(
            MemberRow member, List<PostRow> posts, List<ApplicationRow> applications, List<RatingRow> ratings,
            List<VocResponse> reportsAbout, List<VocResponse> vocsBy, List<ActionRow> actions,
            List<LoginRow> recentLogins, WithdrawalInfo withdrawal, List<DeletionRow> deletions
    ) {
    }

    public record SanctionRequest(
            @NotNull(message = "처리 방법을 골라 주세요.") SanctionAction action,
            @Min(value = 1, message = "정지 기간은 1~365일이에요.")
            @Max(value = 365, message = "정지 기간은 1~365일이에요.") Integer days,
            @Size(max = 200, message = "사유는 200자까지 쓸 수 있어요.") String reason
    ) {
    }

    public record PostVisibilityRequest(
            @NotNull(message = "숨김 여부를 골라 주세요.") Boolean hidden,
            @Size(max = 200, message = "사유는 200자까지 쓸 수 있어요.") String reason
    ) {
    }

    public record VocUpdateRequest(
            VocStatus status,
            @Size(max = 1000, message = "답변은 1000자까지 쓸 수 있어요.") String answer,
            @Size(max = 1000, message = "메모는 1000자까지 쓸 수 있어요.") String adminMemo
    ) {
    }
}
