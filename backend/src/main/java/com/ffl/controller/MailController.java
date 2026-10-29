package com.ffl.controller;

import com.ffl.auth.AuthService;
import com.ffl.entity.User;
import com.ffl.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final AuthService authService;
    private final MailService mailService;

    @GetMapping
    public Map<String, Object> get() {
        return ok(mailService.get(authService.getCurrentUser()));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        User user = authService.getCurrentUser();
        Integer port = null;
        if (body.get("port") != null && !body.get("port").toString().isBlank()) {
            try {
                port = Integer.valueOf(body.get("port").toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("端口不正确");
            }
        }
        return ok(mailService.update(
                user,
                string(body.get("host")),
                port,
                string(body.get("username")),
                string(body.get("password")),
                body.get("ssl") != null && Boolean.parseBoolean(body.get("ssl").toString()),
                string(body.get("recipient"))
        ));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }
}
