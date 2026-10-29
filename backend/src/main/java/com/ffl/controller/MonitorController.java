package com.ffl.controller;

import com.ffl.auth.AdminAuth;
import com.ffl.auth.AuthService;
import com.ffl.client.ShopGate;
import com.ffl.entity.User;
import com.ffl.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final AuthService authService;
    private final MonitorService monitorService;
    private final ShopGate shopGate;

    @GetMapping
    public Map<String, Object> list() {
        return ok(monitorService.list(currentUser()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return ok(monitorService.create(
                currentUser(),
                string(body.get("origin")),
                string(body.get("destination")),
                date(body.get("flightDate")),
                string(body.get("flightNo")),
                bool(body.get("notifyPresence")),
                bool(body.get("notifyQuantity"))
        ));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(monitorService.update(
                currentUser(),
                id,
                string(body.get("origin")),
                string(body.get("destination")),
                date(body.get("flightDate")),
                string(body.get("flightNo")),
                bool(body.get("enabled")),
                bool(body.get("notifyPresence")),
                bool(body.get("notifyQuantity"))
        ));
    }

    @GetMapping("/{id}/history")
    public Map<String, Object> history(@PathVariable Long id) {
        return ok(monitorService.history(currentUser(), id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        monitorService.delete(currentUser(), id);
        return ok(null);
    }

    @PostMapping("/{id}/check")
    public Map<String, Object> check(@PathVariable Long id) {
        if (shopGate.isPaused()) {
            throw new IllegalArgumentException("访问受限，请稍后再查");
        }
        User user = currentUser();
        var monitor = monitorService.loadOwned(user, id);
        if (monitor.getLastCheckedAt() != null
                && monitor.getLastCheckedAt().isAfter(java.time.LocalDateTime.now().minusSeconds(60))) {
            throw new IllegalArgumentException("请至少间隔60秒再查");
        }
        monitorService.checkOne(monitor);
        return ok(monitorService.list(user).stream()
                .filter(item -> id.equals(toLong(item.get("id"))))
                .findFirst()
                .orElse(null));
    }

    private User currentUser() {
        User user = authService.getCurrentUser();
        if (AdminAuth.isAdmin(user)) {
            throw new IllegalArgumentException("管理员不创建监控");
        }
        return user;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate date(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return LocalDate.parse(value.toString());
    }

    private static Boolean bool(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Boolean.valueOf(value.toString());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "ok");
        body.put("data", data);
        return body;
    }
}
