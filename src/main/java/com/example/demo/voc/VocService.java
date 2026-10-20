package com.example.demo.voc;

import com.example.demo.companion.CompanionPost;
import com.example.demo.companion.CompanionPostRepository;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.voc.dto.VocRequest;
import com.example.demo.voc.dto.VocResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VocService {

    private static final int MAX_OPEN_PER_MEMBER = 10;

    private final VocRepository vocRepository;
    private final MemberRepository memberRepository;
    private final CompanionPostRepository postRepository;

    public VocService(VocRepository vocRepository, MemberRepository memberRepository,
                      CompanionPostRepository postRepository) {
        this.vocRepository = vocRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
    }

    @Transactional
    public VocResponse create(Member reporter, VocRequest request) {
        if (vocRepository.countByReporterIdAndStatus(reporter.getId(), VocStatus.OPEN) >= MAX_OPEN_PER_MEMBER) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "처리 중인 문의가 너무 많아요. 답변을 받은 뒤 다시 남겨 주세요.");
        }
        Voc voc = new Voc();
        voc.setType(request.type());
        voc.setReporterId(reporter.getId());
        voc.setReporterNickname(reporter.getNickname());
        voc.setContent(request.content().trim());

        switch (request.type()) {
            case REPORT_MEMBER -> {
                Member target = memberRepository.findById(Objects.requireNonNullElse(request.targetMemberId(), -1L))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고할 회원을 찾을 수 없어요."));
                if (target.getId().equals(reporter.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "나 자신은 신고할 수 없어요.");
                }
                voc.setTargetMemberId(target.getId());
                voc.setTargetPostId(request.targetPostId());
            }
            case REPORT_POST -> {
                CompanionPost post = postRepository.findById(Objects.requireNonNullElse(request.targetPostId(), -1L))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고할 게시글을 찾을 수 없어요."));
                voc.setTargetPostId(post.getId());
                voc.setTargetMemberId(post.getMemberId());
            }
            default -> {
            }
        }
        Instant now = Instant.now();
        voc.setCreatedAt(now);
        voc.setUpdatedAt(now);
        Voc saved = vocRepository.save(voc);
        return VocResponse.forMember(saved, nickname(saved.getTargetMemberId()));
    }

    public List<VocResponse> mine(Member member) {
        List<Voc> vocs = vocRepository.findByReporterIdOrderByCreatedAtDesc(member.getId());
        Map<Long, String> nicknames = nicknames(vocs);
        return vocs.stream()
                .map(v -> VocResponse.forMember(v, nicknames.get(v.getTargetMemberId())))
                .toList();
    }

    /** 관리자 화면용: 대상 회원 닉네임을 한 번에 채워 줍니다. */
    public List<VocResponse> forAdmin(List<Voc> vocs) {
        Map<Long, String> nicknames = nicknames(vocs);
        return vocs.stream()
                .map(v -> VocResponse.forAdmin(v, nicknames.get(v.getTargetMemberId())))
                .toList();
    }

    private Map<Long, String> nicknames(Collection<Voc> vocs) {
        List<Long> ids = vocs.stream().map(Voc::getTargetMemberId).filter(Objects::nonNull).distinct().toList();
        return memberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname, (a, b) -> a));
    }

    private String nickname(Long memberId) {
        return memberId == null ? null : memberRepository.findById(memberId).map(Member::getNickname).orElse(null);
    }
}
