package com.example.demo.companion;

import com.example.demo.companion.dto.ApplicationResponse;
import com.example.demo.companion.dto.ApplicationRequest;
import com.example.demo.companion.dto.CompanionPostRequest;
import com.example.demo.companion.dto.CompanionPostResponse;
import com.example.demo.companion.dto.MyCompanionsResponse;
import com.example.demo.manner.MannerService;
import com.example.demo.manner.dto.MannerSummary;
import com.example.demo.member.BlockService;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CompanionService {

    private final CompanionPostRepository postRepository;
    private final CompanionApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final MannerService mannerService;
    private final BlockService blockService;
    private final ContentDeletionLogRepository deletionLogRepository;

    public CompanionService(CompanionPostRepository postRepository,
                            CompanionApplicationRepository applicationRepository,
                            MemberRepository memberRepository,
                            MannerService mannerService,
                            BlockService blockService,
                            ContentDeletionLogRepository deletionLogRepository) {
        this.postRepository = postRepository;
        this.applicationRepository = applicationRepository;
        this.memberRepository = memberRepository;
        this.mannerService = mannerService;
        this.blockService = blockService;
        this.deletionLogRepository = deletionLogRepository;
    }

    /** viewer 는 로그인하지 않았다면 null. 차단 관계에 있는 회원의 글은 서로 보이지 않습니다. */
    public List<CompanionPostResponse> search(String q, Member viewer) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Set<Long> hiddenAuthors = blockService.related(viewer);
        return postRepository.search(query).stream()
                .filter(p -> p.getMemberId() == null || !hiddenAuthors.contains(p.getMemberId()))
                .map(this::toResponse)
                .toList();
    }

    public CompanionPostResponse get(Long id, Member viewer) {
        CompanionPost post = find(id);
        if (post.hiddenOrDefault() || post.isDeleted() || blockService.blockedEitherWay(viewer, post.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요.");
        }
        return toResponse(post);
    }

    /**
     * 내가 쓴 모집글을 지웁니다. 글은 목록에서 완전히 사라지고, 지우기 직전 내용이 삭제 기록에 남습니다.
     * 이미 수락한 동행이 있는 일정은 먼저 거절 처리해야 지울 수 있어요.
     */
    @Transactional
    public void delete(Long id, Member member) {
        CompanionPost post = find(id);
        if (post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요.");
        }
        if (!isAuthor(post, member)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 쓴 글만 지울 수 있어요.");
        }
        if (!post.getEndDate().isBefore(LocalDate.now())
                && applicationRepository.countByPostIdAndStatus(id, ApplicationStatus.ACCEPTED) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이미 수락한 동행이 있어요. 신청자에게 먼저 안내하고 수락을 취소한 뒤 지워 주세요.");
        }
        softDelete(post, member.getId(), ContentDeletionLog.BY_AUTHOR);
    }

    /** 삭제 기록을 남기고 글을 삭제 처리합니다. 탈퇴 처리에서도 씁니다. */
    @Transactional
    public void softDelete(CompanionPost post, Long actorId, String reason) {
        ContentDeletionLog entry = new ContentDeletionLog();
        entry.setTargetType("POST");
        entry.setTargetId(post.getId());
        entry.setActorId(actorId);
        entry.setReason(reason);
        entry.setSnapshot(post.snapshotText());
        entry.setCreatedAt(Instant.now());
        deletionLogRepository.save(entry);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    /** 내가 쓴 모집글(숨김 포함)과 내가 신청한 모집글 */
    public MyCompanionsResponse mine(Member member) {
        List<CompanionPostResponse> posts = postRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(p -> !p.isDeleted())
                .map(this::toResponse)
                .toList();
        List<MyCompanionsResponse.MyApplication> applications = applicationRepository
                .findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(a -> !a.getPost().hiddenOrDefault() && !a.getPost().isDeleted())
                .map(a -> new MyCompanionsResponse.MyApplication(a.getId(), a.status(), toResponse(a.getPost())))
                .toList();
        return new MyCompanionsResponse(posts, applications);
    }

    @Transactional
    public CompanionPostResponse create(CompanionPostRequest request, Member member) {
        requirePhoneVerified(member);
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없어요.");
        }
        CompanionPost post = new CompanionPost();
        post.setTitle(request.title().trim());
        post.setDestination(request.destination().trim());
        post.setStartDate(request.startDate());
        post.setEndDate(request.endDate());
        post.setRecruitCount(request.recruitCount());
        post.setMemberId(member.getId());
        post.setNickname(member.getNickname());
        post.setContent(request.content().trim());
        post.setStyle(request.style() == null || request.style().isBlank() ? null : request.style().trim());
        post.setCreatedAt(Instant.now());
        return toResponse(postRepository.save(post));
    }

    /** 신청 메시지에는 연락처가 담길 수 있어서, 작성자는 전체를, 신청자는 자기 신청만 봅니다. */
    public List<ApplicationResponse> applications(Long postId, Member viewer) {
        CompanionPost post = find(postId);
        if (post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요.");
        }
        if (!isAuthor(post, viewer)) {
            return toResponses(applicationRepository.findByPostIdAndMemberIdOrderByCreatedAtDesc(postId, viewer.getId()));
        }
        // 작성자가 차단한(또는 작성자를 차단한) 신청자의 신청은 목록에서 빠집니다.
        Set<Long> blocked = blockService.related(viewer);
        return toResponses(applicationRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .filter(a -> a.getMemberId() == null || !blocked.contains(a.getMemberId()))
                .toList());
    }

    @Transactional
    public ApplicationResponse apply(Long postId, ApplicationRequest request, Member member) {
        requirePhoneVerified(member);
        CompanionPost post = find(postId);
        if (post.hiddenOrDefault() || post.isDeleted() || blockService.blockedEitherWay(member, post.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요.");
        }
        if (post.getEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마감된 일정이에요.");
        }
        if (isAuthor(post, member)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 모집글에는 신청할 수 없어요.");
        }
        if (applicationRepository.existsByPostIdAndMemberId(postId, member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 일정이에요.");
        }
        CompanionApplication application = new CompanionApplication();
        application.setPost(post);
        application.setMemberId(member.getId());
        application.setNickname(member.getNickname());
        application.setMessage(request.message().trim());
        application.setStatus(ApplicationStatus.PENDING);
        application.setCreatedAt(Instant.now());
        return toResponses(List.of(applicationRepository.save(application))).get(0);
    }

    @Transactional
    public ApplicationResponse decide(Long postId, Long applicationId, ApplicationStatus status, Member viewer) {
        CompanionPost post = find(postId);
        if (post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요.");
        }
        if (!isAuthor(post, viewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "모집글 작성자만 수락하거나 거절할 수 있어요.");
        }
        if (status == ApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수락 또는 거절을 골라 주세요.");
        }
        CompanionApplication application = applicationRepository.findByIdAndPostId(applicationId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청을 찾을 수 없어요."));
        if (status == ApplicationStatus.ACCEPTED
                && application.status() != ApplicationStatus.ACCEPTED
                && applicationRepository.countByPostIdAndStatus(postId, ApplicationStatus.ACCEPTED) >= post.getRecruitCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "모집 인원이 다 찼어요.");
        }
        application.setStatus(status);
        return toResponses(List.of(applicationRepository.save(application))).get(0);
    }

    private List<ApplicationResponse> toResponses(List<CompanionApplication> applications) {
        Set<Long> memberIds = applications.stream()
                .map(CompanionApplication::getMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, MannerSummary> manners = mannerService.summaries(memberIds);
        return applications.stream()
                .map(a -> {
                    Member applicant = a.getMemberId() == null ? null : members.get(a.getMemberId());
                    return ApplicationResponse.from(a,
                            applicant != null && applicant.isPhoneVerified(),
                            a.getMemberId() == null ? MannerSummary.EMPTY
                                    : manners.getOrDefault(a.getMemberId(), MannerSummary.EMPTY));
                })
                .toList();
    }

    private static boolean isAuthor(CompanionPost post, Member member) {
        return post.getMemberId() != null && post.getMemberId().equals(member.getId());
    }

    private static void requirePhoneVerified(Member member) {
        if (!member.isPhoneVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "같이가요는 안전을 위해 휴대폰 인증을 마친 회원만 이용할 수 있어요.");
        }
    }

    private CompanionPost find(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요."));
    }

    private CompanionPostResponse toResponse(CompanionPost post) {
        return CompanionPostResponse.from(post,
                applicationRepository.countByPostId(post.getId()),
                applicationRepository.countByPostIdAndStatus(post.getId(), ApplicationStatus.ACCEPTED));
    }
}
