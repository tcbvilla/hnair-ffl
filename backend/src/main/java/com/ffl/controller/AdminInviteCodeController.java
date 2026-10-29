package com.ffl.controller;

import com.ffl.auth.AdminAuth;
import com.ffl.auth.AuthService;
import com.ffl.entity.User;
import com.ffl.service.InviteCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invite-codes")
@RequiredArgsConstructor
public class AdminInviteCodeController {

    private final AuthService authService;
    private final InviteCodeService inviteCodeService;

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String status) {
        requireAdmin();
        return ok(inviteCodeService.list(status, page, size));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Integer> body) {
        User admin = requireAdmin();
        int count = body.getOrDefault("count", 1);
        Map<String, Object> data = new HashMap<>();
        data.put("codes", inviteCodeService.generate(admin, count));
        return ok(data);
    }

    private User requireAdmin() {
        User user = authService.getCurrentUser();
        AdminAuth.requireAdmin(user);
        return user;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }
}
