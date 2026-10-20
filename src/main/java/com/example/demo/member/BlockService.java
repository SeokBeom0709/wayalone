package com.example.demo.member;

import com.example.demo.member.dto.BlockedMemberResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 회원끼리 차단. 차단하면 서로의 모집글이 보이지 않고 신청도 할 수 없습니다. */
@Service
@Transactional(readOnly = true)
public class BlockService {

    private final MemberBlockRepository blockRepository;
    private final MemberRepository memberRepository;

    public BlockService(MemberBlockRepository blockRepository, MemberRepository memberRepository) {
        this.blockRepository = blockRepository;
        this.memberRepository = memberRepository;
    }

    public List<BlockedMemberResponse> list(Member me) {
        List<MemberBlock> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(me.getId());
        Map<Long, Member> members = memberRepository
                .findAllById(blocks.stream().map(MemberBlock::getBlockedId).toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return blocks.stream()
                .map(b -> new BlockedMemberResponse(b.getBlockedId(),
                        members.containsKey(b.getBlockedId()) ? members.get(b.getBlockedId()).getNickname() : "(알 수 없음)",
                        b.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void block(Member me, Long targetId) {
        if (me.getId().equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "나 자신은 차단할 수 없어요.");
        }
        Member target = memberRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요."));
        if (target.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운영자는 차단할 수 없어요. 문제가 있다면 신고해 주세요.");
        }
        if (target.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 탈퇴한 회원이에요.");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(me.getId(), targetId)) {
            return;
        }
        MemberBlock block = new MemberBlock();
        block.setBlockerId(me.getId());
        block.setBlockedId(targetId);
        block.setCreatedAt(Instant.now());
        blockRepository.save(block);
    }

    @Transactional
    public void unblock(Member me, Long targetId) {
        blockRepository.deletePair(me.getId(), targetId);
    }

    /** 내가 차단했거나 나를 차단한 회원 id. 로그인하지 않았으면 비어 있습니다. */
    public Set<Long> related(Member viewer) {
        return viewer == null ? Set.of() : blockRepository.relatedIds(viewer.getId());
    }

    public boolean blockedEitherWay(Member viewer, Long otherId) {
        return viewer != null && otherId != null && blockRepository.existsEitherWay(viewer.getId(), otherId);
    }
}
