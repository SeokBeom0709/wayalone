package com.example.demo.admin;

import com.example.demo.admin.AdminDtos.ActionRow;
import com.example.demo.admin.AdminDtos.ApplicationRow;
import com.example.demo.admin.AdminDtos.BlacklistRow;
import com.example.demo.admin.AdminDtos.DailyPoint;
import com.example.demo.admin.AdminDtos.DeletionRow;
import com.example.demo.admin.AdminDtos.LoginRow;
import com.example.demo.admin.AdminDtos.WithdrawalInfo;
import com.example.demo.admin.AdminDtos.MemberDetail;
import com.example.demo.admin.AdminDtos.MemberPage;
import com.example.demo.admin.AdminDtos.MemberRow;
import com.example.demo.admin.AdminDtos.PostRow;
import com.example.demo.admin.AdminDtos.RatingRow;
import com.example.demo.admin.AdminDtos.SanctionRequest;
import com.example.demo.admin.AdminDtos.Stats;
import com.example.demo.admin.AdminDtos.VocUpdateRequest;
import com.example.demo.companion.CompanionApplicationRepository;
import com.example.demo.companion.CompanionPost;
import com.example.demo.companion.CompanionPostRepository;
import com.example.demo.companion.ContentDeletionLogRepository;
import com.example.demo.manner.MannerRating;
import com.example.demo.manner.MannerRatingRepository;
import com.example.demo.manner.MannerService;
import com.example.demo.manner.MannerTag;
import com.example.demo.manner.dto.MannerSummary;
import com.example.demo.member.AuthTokenRepository;
import com.example.demo.member.LoginHistoryRepository;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.member.MemberStatus;
import com.example.demo.member.MemberWithdrawalRepository;
import com.example.demo.member.PhoneBlacklist;
import com.example.demo.member.PhoneBlacklistRepository;
import com.example.demo.voc.Voc;
import com.example.demo.voc.VocRepository;
import com.example.demo.voc.VocService;
import com.example.demo.voc.VocStatus;
import com.example.demo.voc.VocType;
import com.example.demo.voc.dto.VocResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final int PAGE_SIZE = 20;
    private static final int DAILY_DAYS = 14;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<VocType> REPORT_TYPES = List.of(VocType.REPORT_MEMBER, VocType.REPORT_POST);

    private final MemberRepository memberRepository;
    private final AuthTokenRepository tokenRepository;
    private final CompanionPostRepository postRepository;
    private final CompanionApplicationRepository applicationRepository;
    private final MannerRatingRepository ratingRepository;
    private final MannerService mannerService;
    private final VocRepository vocRepository;
    private final VocService vocService;
    private final AdminActionRepository actionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final MemberWithdrawalRepository withdrawalRepository;
    private final PhoneBlacklistRepository blacklistRepository;
    private final ContentDeletionLogRepository deletionLogRepository;

    public AdminService(MemberRepository memberRepository, AuthTokenRepository tokenRepository,
                        CompanionPostRepository postRepository, CompanionApplicationRepository applicationRepository,
                        MannerRatingRepository ratingRepository, MannerService mannerService,
                        VocRepository vocRepository, VocService vocService, AdminActionRepository actionRepository,
                        LoginHistoryRepository loginHistoryRepository, MemberWithdrawalRepository withdrawalRepository,
                        PhoneBlacklistRepository blacklistRepository,
                        ContentDeletionLogRepository deletionLogRepository) {
        this.loginHistoryRepository = loginHistoryRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.blacklistRepository = blacklistRepository;
        this.deletionLogRepository = deletionLogRepository;
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.postRepository = postRepository;
        this.applicationRepository = applicationRepository;
        this.ratingRepository = ratingRepository;
        this.mannerService = mannerService;
        this.vocRepository = vocRepository;
        this.vocService = vocService;
        this.actionRepository = actionRepository;
    }

    public static void requireAdmin(Member member) {
        if (!member.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있어요.");
        }
    }

    public Stats stats() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(KST);
        LocalDate first = today.minusDays(DAILY_DAYS - 1);
        Instant since = first.atStartOfDay(KST).toInstant();
        Map<LocalDate, Long> members = byDay(memberRepository.createdSince(since));
        Map<LocalDate, Long> posts = byDay(postRepository.createdSince(since));
        Map<LocalDate, Long> applications = byDay(applicationRepository.createdSince(since));
        Map<LocalDate, Long> vocs = byDay(vocRepository.createdSince(since));
        List<DailyPoint> daily = first.datesUntil(today.plusDays(1))
                .map(d -> new DailyPoint(d, members.getOrDefault(d, 0L), posts.getOrDefault(d, 0L),
                        applications.getOrDefault(d, 0L), vocs.getOrDefault(d, 0L)))
                .toList();
        return new Stats(
                memberRepository.count(),
                memberRepository.countByCreatedAtAfter(now.minus(Duration.ofDays(1))),
                memberRepository.countByCreatedAtAfter(now.minus(Duration.ofDays(7))),
                memberRepository.countByPhoneHashIsNotNull(),
                memberRepository.countByStatus(MemberStatus.SUSPENDED),
                memberRepository.countByStatus(MemberStatus.BANNED),
                memberRepository.countByStatus(MemberStatus.WITHDRAWN),
                blacklistRepository.count(),
                postRepository.count(),
                postRepository.countHidden(),
                applicationRepository.count(),
                ratingRepository.count(),
                ratingRepository.countNoShows(),
                vocRepository.countByStatus(VocStatus.OPEN),
                vocRepository.countByStatus(VocStatus.IN_PROGRESS),
                vocRepository.countByStatus(VocStatus.RESOLVED),
                daily);
    }

    private static Map<LocalDate, Long> byDay(List<Instant> instants) {
        return instants.stream().collect(Collectors.groupingBy(i -> i.atZone(KST).toLocalDate(), Collectors.counting()));
    }

    public MemberPage members(String q, MemberStatus status, int page) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Member> result = memberRepository.adminSearch(query, status, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        List<Long> ids = result.getContent().stream().map(Member::getId).toList();
        Map<Long, MannerSummary> manners = mannerService.summaries(ids);
        Map<Long, Long> reports = reportCounts(ids);
        List<MemberRow> rows = result.getContent().stream()
                .map(m -> MemberRow.of(m, manners.getOrDefault(m.getId(), MannerSummary.EMPTY),
                        reports.getOrDefault(m.getId(), 0L)))
                .toList();
        return new MemberPage(rows, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    public MemberDetail member(Long id) {
        Member member = findMember(id);
        List<PostRow> posts = postRepository.findByMemberIdOrderByCreatedAtDesc(id).stream()
                .map(p -> new PostRow(p.getId(), p.getTitle(), p.getDestination(), p.getStartDate(),
                        p.hiddenOrDefault(), p.isDeleted(), applicationRepository.countByPostId(p.getId()),
                        p.getCreatedAt()))
                .toList();
        List<ApplicationRow> applications = applicationRepository.findByMemberIdOrderByCreatedAtDesc(id).stream()
                .map(a -> new ApplicationRow(a.getId(), a.getPost().getId(), a.getPost().getTitle(), a.status(),
                        a.getMessage(), a.getCreatedAt()))
                .toList();

        List<MannerRating> received = ratingRepository.findByTargetIdOrderByCreatedAtDesc(id);
        Map<Long, String> raters = memberRepository.findAllById(received.stream().map(MannerRating::getRaterId).toList())
                .stream().collect(Collectors.toMap(Member::getId, Member::getNickname, (a, b) -> a));
        List<RatingRow> ratings = received.stream()
                .map(r -> new RatingRow(r.getPostId(), r.getRaterId(), raters.getOrDefault(r.getRaterId(), "(탈퇴)"),
                        r.getScore(), r.attendedOrDefault(), r.tagList().stream().map(MannerTag::label).toList(),
                        r.getCreatedAt()))
                .toList();

        return new MemberDetail(
                MemberRow.of(member, MannerSummary.of(received), reportCounts(List.of(id)).getOrDefault(id, 0L)),
                posts,
                applications,
                ratings,
                vocService.forAdmin(vocRepository.findByTargetMemberIdOrderByCreatedAtDesc(id)),
                vocService.forAdmin(vocRepository.findByReporterIdOrderByCreatedAtDesc(id)),
                memberActions(id),
                loginHistoryRepository.findTop20ByMemberIdOrderByIdDesc(id).stream().map(LoginRow::of).toList(),
                withdrawalRepository.findByMemberId(id).map(WithdrawalInfo::of).orElse(null),
                deletionLogRepository.findTop20ByActorIdOrderByIdDesc(id).stream().map(DeletionRow::of).toList());
    }

    /** 영구 차단된 번호 목록 (해시 앞부분만) */
    public List<BlacklistRow> blacklist() {
        return blacklistRepository.findAllByOrderByIdDesc().stream().map(BlacklistRow::of).toList();
    }

    private List<ActionRow> memberActions(Long id) {
        Long latest = latestEffectiveId("MEMBER", id);
        return actionRepository.findByTargetTypeAndTargetIdOrderByIdDesc("MEMBER", id).stream()
                .map(a -> ActionRow.of(a, revertible(a, latest)))
                .toList();
    }

    @Transactional
    public MemberRow sanction(Member admin, Long id, SanctionRequest request) {
        Member member = findMember(id);
        if (member.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "관리자 계정은 제재할 수 없어요.");
        }
        if (member.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 탈퇴한 회원이라 제재할 수 없어요.");
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        Instant now = Instant.now();
        String prevStatus = (member.getStatus() == null ? MemberStatus.ACTIVE : member.getStatus()).name();
        Instant prevUntil = member.getSuspendedUntil();
        String prevReason = member.getStatusReason();
        String detail;
        switch (request.action()) {
            case SUSPEND -> {
                if (request.days() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정지 기간을 입력해 주세요.");
                }
                requireReason(reason);
                member.setStatus(MemberStatus.SUSPENDED);
                member.setSuspendedUntil(now.plus(Duration.ofDays(request.days())));
                member.setStatusReason(reason);
                tokenRepository.deleteAllByMember(id);
                detail = request.days() + "일 정지 · " + reason;
            }
            case BAN -> {
                requireReason(reason);
                member.setStatus(MemberStatus.BANNED);
                member.setSuspendedUntil(null);
                member.setStatusReason(reason);
                tokenRepository.deleteAllByMember(id);
                detail = "영구 차단 · " + reason;
            }
            default -> {
                member.setStatus(MemberStatus.ACTIVE);
                member.setSuspendedUntil(null);
                member.setStatusReason(null);
                detail = "제한 해제" + (reason == null ? "" : " · " + reason);
            }
        }
        memberRepository.save(member);
        syncBlacklist(member, admin.getId());
        log(admin, request.action().name(), "MEMBER", id, detail, a -> {
            a.setPrevStatus(prevStatus);
            a.setPrevUntil(prevUntil);
            a.setPrevText1(prevReason);
        });
        return MemberRow.of(member, mannerService.summary(id), reportCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public void setPostHidden(Member admin, Long postId, boolean hidden, String reason) {
        CompanionPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요."));
        boolean prevHidden = post.hiddenOrDefault();
        post.setHidden(hidden);
        postRepository.save(post);
        String why = reason == null || reason.isBlank() ? "" : " · " + reason.trim();
        log(admin, hidden ? "HIDE_POST" : "SHOW_POST", "POST", postId, post.getTitle() + why,
                a -> a.setPrevFlag(prevHidden));
    }

    public List<VocResponse> vocs(VocStatus status) {
        List<Voc> vocs = status == null
                ? vocRepository.findAllByOrderByCreatedAtDesc()
                : vocRepository.findByStatusOrderByCreatedAtDesc(status);
        return vocService.forAdmin(vocs);
    }

    @Transactional
    public VocResponse updateVoc(Member admin, Long id, VocUpdateRequest request) {
        Voc voc = vocRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없어요."));
        Instant now = Instant.now();
        String prevStatus = voc.getStatus().name();
        Instant prevResolvedAt = voc.getResolvedAt();
        String prevAnswer = voc.getAnswer();
        String prevMemo = voc.getAdminMemo();
        if (request.status() != null) {
            voc.setStatus(request.status());
            voc.setResolvedAt(request.status() == VocStatus.RESOLVED ? now : null);
        }
        if (request.answer() != null) {
            voc.setAnswer(request.answer().isBlank() ? null : request.answer().trim());
        }
        if (request.adminMemo() != null) {
            voc.setAdminMemo(request.adminMemo().isBlank() ? null : request.adminMemo().trim());
        }
        voc.setUpdatedAt(now);
        vocRepository.save(voc);
        log(admin, "VOC_UPDATE", "VOC", id, voc.getStatus() + (voc.getAnswer() != null ? " · 답변 등록" : ""), a -> {
            a.setPrevStatus(prevStatus);
            a.setPrevUntil(prevResolvedAt);
            a.setPrevText1(prevAnswer);
            a.setPrevText2(prevMemo);
        });
        return vocService.forAdmin(List.of(voc)).get(0);
    }

    public List<ActionRow> actions() {
        List<AdminAction> recent = actionRepository.findTop100ByOrderByCreatedAtDesc();
        Map<String, Long> latest = new HashMap<>();
        recent.forEach(a -> latest.computeIfAbsent(targetKey(a),
                k -> latestEffectiveId(a.getTargetType(), a.getTargetId())));
        return recent.stream().map(a -> ActionRow.of(a, revertible(a, latest.get(targetKey(a))))).toList();
    }

    /**
     * 잘못 처리한 건을 처리 직전 상태로 되돌립니다. 원래 기록은 그대로 두고 REVERT 기록을 새로 남깁니다.
     * 같은 대상에 더 최근 처리가 있으면 최근 것부터 차례로 되돌려야 상태가 꼬이지 않습니다.
     */
    @Transactional
    public ActionRow revert(Member admin, Long actionId, String reason) {
        AdminAction original = actionRepository.findById(actionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "처리 기록을 찾을 수 없어요."));
        if (original.isRevert()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "되돌리기 기록은 다시 되돌릴 수 없어요. 필요한 처리를 새로 해 주세요.");
        }
        if (original.getRevertedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 되돌린 처리예요.");
        }
        if (!original.hasSnapshot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이전 상태가 기록되지 않은 예전 처리라 되돌릴 수 없어요.");
        }
        Long latest = latestEffectiveId(original.getTargetType(), original.getTargetId());
        if (!original.getId().equals(latest)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이 대상에 더 최근 처리가 있어요. 최근 처리부터 차례로 되돌려 주세요.");
        }

        Long targetId = original.getTargetId();
        switch (original.getTargetType()) {
            case "MEMBER" -> {
                Member member = findMember(targetId);
                if (member.isWithdrawn()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 탈퇴한 회원이라 되돌릴 수 없어요.");
                }
                member.setStatus(MemberStatus.valueOf(original.getPrevStatus()));
                member.setSuspendedUntil(original.getPrevUntil());
                member.setStatusReason(original.getPrevText1());
                if (member.isBlocked(Instant.now())) {
                    tokenRepository.deleteAllByMember(targetId);
                }
                memberRepository.save(member);
                syncBlacklist(member, admin.getId());
            }
            case "POST" -> {
                CompanionPost post = postRepository.findById(targetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요."));
                post.setHidden(original.getPrevFlag());
                postRepository.save(post);
            }
            case "VOC" -> {
                Voc voc = vocRepository.findById(targetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없어요."));
                voc.setStatus(VocStatus.valueOf(original.getPrevStatus()));
                voc.setResolvedAt(original.getPrevUntil());
                voc.setAnswer(original.getPrevText1());
                voc.setAdminMemo(original.getPrevText2());
                voc.setUpdatedAt(Instant.now());
                vocRepository.save(voc);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "되돌릴 수 없는 처리예요.");
        }

        original.setRevertedAt(Instant.now());
        original.setRevertedById(admin.getId());
        original.setRevertedByNickname(admin.getNickname());
        actionRepository.save(original);
        AdminAction revert = log(admin, "REVERT", original.getTargetType(), targetId,
                "#" + original.getId() + " " + original.getAction() + " 되돌림 · " + reason.trim(),
                a -> a.setRevertOfId(original.getId()));
        return ActionRow.of(revert, false);
    }

    /** 대상의 '현재 상태를 만든' 마지막 처리 id. REVERT 기록과 이미 되돌린 처리는 건너뜁니다. */
    private Long latestEffectiveId(String targetType, Long targetId) {
        return actionRepository.findByTargetTypeAndTargetIdOrderByIdDesc(targetType, targetId).stream()
                .filter(a -> !a.isRevert() && a.getRevertedAt() == null)
                .map(AdminAction::getId)
                .findFirst()
                .orElse(null);
    }

    private static boolean revertible(AdminAction a, Long latestEffectiveId) {
        return !a.isRevert() && a.getRevertedAt() == null && a.hasSnapshot() && a.getId().equals(latestEffectiveId);
    }

    private static String targetKey(AdminAction a) {
        return a.getTargetType() + ":" + a.getTargetId();
    }

    private Map<Long, Long> reportCounts(List<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        for (Object[] row : vocRepository.countReportsByTarget(ids, REPORT_TYPES)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private AdminAction log(Member admin, String action, String targetType, Long targetId, String detail,
                            Consumer<AdminAction> snapshot) {
        AdminAction entry = new AdminAction();
        entry.setAdminId(admin.getId());
        entry.setAdminNickname(admin.getNickname());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)));
        entry.setCreatedAt(Instant.now());
        snapshot.accept(entry);
        return actionRepository.save(entry);
    }

    /** 영구 차단이면 번호 해시를 블랙리스트에 올리고, 차단이 풀리면 내립니다. */
    private void syncBlacklist(Member member, Long adminId) {
        String phoneHash = member.getPhoneHash();
        if (phoneHash == null) {
            return;
        }
        boolean listed = blacklistRepository.existsByPhoneHash(phoneHash);
        if (member.getStatus() == MemberStatus.BANNED && !listed) {
            PhoneBlacklist entry = new PhoneBlacklist();
            entry.setPhoneHash(phoneHash);
            entry.setReason(member.getStatusReason());
            entry.setSourceMemberId(member.getId());
            entry.setCreatedById(adminId);
            entry.setCreatedAt(Instant.now());
            blacklistRepository.save(entry);
        } else if (member.getStatus() != MemberStatus.BANNED && listed) {
            blacklistRepository.deleteByPhoneHash(phoneHash);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제재 사유를 입력해 주세요. 회원에게 안내돼요.");
        }
    }

    private Member findMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요."));
    }
}
