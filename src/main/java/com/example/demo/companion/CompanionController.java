package com.example.demo.companion;

import com.example.demo.companion.dto.ApplicationDecisionRequest;
import com.example.demo.companion.dto.ApplicationRequest;
import com.example.demo.companion.dto.ApplicationResponse;
import com.example.demo.companion.dto.CompanionPostRequest;
import com.example.demo.companion.dto.CompanionPostResponse;
import com.example.demo.companion.dto.MyCompanionsResponse;
import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companions")
public class CompanionController {

    private final CompanionService companionService;

    public CompanionController(CompanionService companionService) {
        this.companionService = companionService;
    }

    @GetMapping
    public List<CompanionPostResponse> search(@RequestParam(required = false) String q,
                                              @LoginMember(required = false) Member viewer) {
        return companionService.search(q, viewer);
    }

    @GetMapping("/mine")
    public MyCompanionsResponse mine(@LoginMember Member member) {
        return companionService.mine(member);
    }

    @GetMapping("/{id}")
    public CompanionPostResponse get(@PathVariable Long id, @LoginMember(required = false) Member viewer) {
        return companionService.get(id, viewer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @LoginMember Member member) {
        companionService.delete(id, member);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanionPostResponse create(@LoginMember Member member,
                                        @Valid @RequestBody CompanionPostRequest request) {
        return companionService.create(request, member);
    }

    @GetMapping("/{id}/applications")
    public List<ApplicationResponse> applications(@PathVariable Long id, @LoginMember Member member) {
        return companionService.applications(id, member);
    }

    @PatchMapping("/{id}/applications/{applicationId}")
    public ApplicationResponse decide(@PathVariable Long id,
                                      @PathVariable Long applicationId,
                                      @LoginMember Member member,
                                      @Valid @RequestBody ApplicationDecisionRequest request) {
        return companionService.decide(id, applicationId, request.status(), member);
    }

    @PostMapping("/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@PathVariable Long id,
                                     @LoginMember Member member,
                                     @Valid @RequestBody ApplicationRequest request) {
        return companionService.apply(id, request, member);
    }
}
