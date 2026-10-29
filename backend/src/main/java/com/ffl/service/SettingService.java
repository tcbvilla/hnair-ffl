package com.ffl.service;

import com.ffl.entity.AppSetting;
import com.ffl.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {

    public static final int MIN_INTERVAL_SECONDS = 60;
    public static final int MAX_INTERVAL_SECONDS = 3600;

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public AppSetting current() {
        return appSettingRepository.findById(AppSetting.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Monitor settings are missing"));
    }

    @Transactional
    public AppSetting update(int intervalSeconds, int targetPrice) {
        if (intervalSeconds < MIN_INTERVAL_SECONDS || intervalSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("监控间隔需在60到3600秒之间");
        }
        if (targetPrice < 1 || targetPrice > 99999) {
            throw new IllegalArgumentException("目标价格不正确");
        }
        AppSetting setting = current();
        setting.setIntervalSeconds(intervalSeconds);
        setting.setTargetPrice(targetPrice);
        return appSettingRepository.save(setting);
    }
}
