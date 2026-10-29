package com.ffl.controller;

import com.ffl.auth.AdminAuth;
import com.ffl.auth.AuthService;
import com.ffl.auth.TokenService;
import com.ffl.entity.User;
import com.ffl.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AuthService authService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        User user = userService.register(body.get("username"), body.get("password"), body.get("inviteCode"));
        return ok("注册成功", session(user, tokenService.issueToken(user.getId())));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        User user = userService.login(body.get("username"), body.get("password"));
        return ok("登录成功", session(user, tokenService.issueToken(user.getId())));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : "";
        return ok("ok", session(authService.getCurrentUser(), token));
    }

    private Map<String, Object> session(User user, String token) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("admin", AdminAuth.isAdmin(user));
        return data;
    }

    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        return body;
    }
}
