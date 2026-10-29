package com.ffl.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TokenService {

    private static final long TOKEN_TTL_SECONDS = 7 * 24 * 3600L;

    private static class TokenInfo {
        final Long userId;
        final Instant expiresAt;

        TokenInfo(Long userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenInfo(userId, Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
        log.info("Issued token for user {}", userId);
        return token;
    }

    public Long getUserId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return null;
        }
        if (info.expiresAt.isBefore(Instant.now())) {
            tokens.remove(token);
            return null;
        }
        return info.userId;
    }

    public void revokeToken(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
