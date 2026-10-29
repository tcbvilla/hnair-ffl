package com.ffl.controller;

import com.ffl.auth.AuthService;
import com.ffl.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final AuthService authService;
    private final SettingService settingService;

    @GetMapping
    public Map<String, Object> get() {
        authService.getCurrentUser();
        var setting = settingService.current();
        Map<String, Object> data = new HashMap<>();
        data.put("intervalSeconds", setting.getIntervalSeconds());
        data.put("targetPrice", setting.getTargetPrice());
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }
}
