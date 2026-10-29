package com.ffl.service;

import com.ffl.client.ShopGate;
import com.ffl.entity.Monitor;
import com.ffl.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitorScheduler {

    private final MonitorRepository monitorRepository;
    private final MonitorService monitorService;
    private final SettingService settingService;
    private final ShopGate shopGate;

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void poll() {
        if (shopGate.isPaused()) {
            return;
        }
        int interval = settingService.current().getIntervalSeconds();
        LocalDateTime dueBefore = LocalDateTime.now().minusSeconds(interval);
        List<Monitor> due = monitorRepository.findByEnabledTrueOrderByLastCheckedAtAsc().stream()
                .filter(monitor -> monitor.getLastCheckedAt() == null || monitor.getLastCheckedAt().isBefore(dueBefore))
                .sorted(Comparator.comparing(Monitor::getLastCheckedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        for (Monitor monitor : due) {
            if (shopGate.isPaused()) {
                log.warn("Airline access limited, skip remaining monitors");
                return;
            }
            boolean limited = monitorService.checkOne(monitor);
            if (limited) {
                log.warn("Airline access limited, polling paused");
                return;
            }
        }
    }
}
