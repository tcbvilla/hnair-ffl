package com.ffl.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ShopGate {

    private final HnairClient hnairClient;
    private final Object lock = new Object();
    private long lastCallAt;
    private volatile long pausedUntil;

    @Value("${ffl.request-gap-millis:3000}")
    private long requestGapMillis;

    @Value("${ffl.backoff-seconds:600}")
    private long backoffSeconds;

    public boolean isPaused() {
        return System.currentTimeMillis() < pausedUntil;
    }

    public HnairClient.ShopResult search(String origin, String destination, LocalDate date) {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (now < pausedUntil) {
                return HnairClient.ShopResult.failed("访问受限，暂停查询", false);
            }
            long wait = lastCallAt + requestGapMillis - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return HnairClient.ShopResult.failed("interrupted", false);
                }
            }
            HnairClient.ShopResult result = hnairClient.search(origin, destination, date);
            lastCallAt = System.currentTimeMillis();
            if (result.riskLimited()) {
                pausedUntil = lastCallAt + backoffSeconds * 1000L;
            }
            return result;
        }
    }
}
