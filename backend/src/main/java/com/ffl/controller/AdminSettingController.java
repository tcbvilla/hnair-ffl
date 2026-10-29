package com.ffl.controller;

import com.ffl.auth.AdminAuth;
import com.ffl.auth.AuthService;
import com.ffl.entity.AppSetting;
import com.ffl.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final AuthService authService;
    private final SettingService settingService;

    @GetMapping
    public Map<String, Object> get() {
        AdminAuth.requireAdmin(authService.getCurrentUser());
        return ok(toMap(settingService.current()));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Integer> body) {
        AdminAuth.requireAdmin(authService.getCurrentUser());
        AppSetting setting = settingService.update(body.getOrDefault("intervalSeconds", 0),
                body.getOrDefault("targetPrice", 0));
        return ok(toMap(setting));
    }

    private Map<String, Object> toMap(AppSetting setting) {
        Map<String, Object> data = new HashMap<>();
        data.put("intervalSeconds", setting.getIntervalSeconds());
        data.put("targetPrice", setting.getTargetPrice());
        return data;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }
}
