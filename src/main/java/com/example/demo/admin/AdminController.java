package com.example.demo.admin;

import com.example.demo.admin.AdminDtos.ActionRow;
import com.example.demo.admin.AdminDtos.BlacklistRow;
import com.example.demo.admin.AdminDtos.MemberDetail;
import com.example.demo.admin.AdminDtos.MemberPage;
import com.example.demo.admin.AdminDtos.MemberRow;
import com.example.demo.admin.AdminDtos.PostVisibilityRequest;
import com.example.demo.admin.AdminDtos.RevertRequest;
import com.example.demo.admin.AdminDtos.SanctionRequest;
import com.example.demo.admin.AdminDtos.Stats;
import com.example.demo.admin.AdminDtos.VocUpdateRequest;
import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import com.example.demo.member.MemberStatus;
import com.example.demo.voc.VocStatus;
import com.example.demo.voc.dto.VocResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public Stats stats(@LoginMember Member admin) {
        AdminService.requireAdmin(admin);
        return adminService.stats();
    }

    @GetMapping("/members")
    public MemberPage members(@LoginMember Member admin,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) MemberStatus status,
                              @RequestParam(defaultValue = "0") int page) {
        AdminService.requireAdmin(admin);
        return adminService.members(q, status, page);
    }

    @GetMapping("/members/{id}")
    public MemberDetail member(@LoginMember Member admin, @PathVariable Long id) {
        AdminService.requireAdmin(admin);
        return adminService.member(id);
    }

    @PostMapping("/members/{id}/sanction")
    public MemberRow sanction(@LoginMember Member admin, @PathVariable Long id,
                              @Valid @RequestBody SanctionRequest request) {
        AdminService.requireAdmin(admin);
        return adminService.sanction(admin, id, request);
    }

    @PatchMapping("/companions/{id}/visibility")
    public Map<String, Boolean> postVisibility(@LoginMember Member admin, @PathVariable Long id,
                                               @Valid @RequestBody PostVisibilityRequest request) {
        AdminService.requireAdmin(admin);
        adminService.setPostHidden(admin, id, request.hidden(), request.reason());
        return Map.of("hidden", request.hidden());
    }

    @GetMapping("/vocs")
    public List<VocResponse> vocs(@LoginMember Member admin, @RequestParam(required = false) VocStatus status) {
        AdminService.requireAdmin(admin);
        return adminService.vocs(status);
    }

    @PatchMapping("/vocs/{id}")
    public VocResponse updateVoc(@LoginMember Member admin, @PathVariable Long id,
                                 @Valid @RequestBody VocUpdateRequest request) {
        AdminService.requireAdmin(admin);
        return adminService.updateVoc(admin, id, request);
    }

    /** 영구 차단된 휴대폰 번호 목록 (번호 원문은 저장되지 않아 해시 앞부분만 보입니다) */
    @GetMapping("/blacklist")
    public List<BlacklistRow> blacklist(@LoginMember Member admin) {
        AdminService.requireAdmin(admin);
        return adminService.blacklist();
    }

    @GetMapping("/actions")
    public List<ActionRow> actions(@LoginMember Member admin) {
        AdminService.requireAdmin(admin);
        return adminService.actions();
    }

    @PostMapping("/actions/{id}/revert")
    public ActionRow revert(@LoginMember Member admin, @PathVariable Long id,
                            @Valid @RequestBody RevertRequest request) {
        AdminService.requireAdmin(admin);
        return adminService.revert(admin, id, request.reason());
    }
}
