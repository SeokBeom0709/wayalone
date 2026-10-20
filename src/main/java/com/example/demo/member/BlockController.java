package com.example.demo.member;

import com.example.demo.member.dto.BlockedMemberResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    public List<BlockedMemberResponse> list(@LoginMember Member member) {
        return blockService.list(member);
    }

    @PostMapping("/{memberId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@PathVariable Long memberId, @LoginMember Member member) {
        blockService.block(member, memberId);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable Long memberId, @LoginMember Member member) {
        blockService.unblock(member, memberId);
    }
}
