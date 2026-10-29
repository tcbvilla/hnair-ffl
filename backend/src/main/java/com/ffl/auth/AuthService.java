package com.ffl.auth;

import com.ffl.entity.User;
import com.ffl.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class AuthService {

    private final TokenService tokenService;
    private final UserService userService;

    public User getCurrentUser() {
        Long userId = getCurrentUserIdOrNull();
        if (userId == null) {
            throw new UnauthorizedException("未登录或登录已过期，请重新登录");
        }
        return userService.getUserById(userId);
    }

    public Long getCurrentUserIdOrNull() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return tokenService.getUserId(header.substring(7).trim());
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
