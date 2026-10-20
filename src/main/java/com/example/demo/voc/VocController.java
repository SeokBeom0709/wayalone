package com.example.demo.voc;

import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import com.example.demo.voc.dto.VocRequest;
import com.example.demo.voc.dto.VocResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vocs")
public class VocController {

    private final VocService vocService;

    public VocController(VocService vocService) {
        this.vocService = vocService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VocResponse create(@LoginMember Member member, @Valid @RequestBody VocRequest request) {
        return vocService.create(member, request);
    }

    @GetMapping("/mine")
    public List<VocResponse> mine(@LoginMember Member member) {
        return vocService.mine(member);
    }
}
