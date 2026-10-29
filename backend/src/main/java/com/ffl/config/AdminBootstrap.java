package com.ffl.config;

import com.ffl.auth.AdminAuth;
import com.ffl.entity.User;
import com.ffl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@system.local";
    private static final String ADMIN_PASSWORD = "admin123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername(AdminAuth.ADMIN_USERNAME)) {
            User admin = new User();
            admin.setUsername(AdminAuth.ADMIN_USERNAME);
            admin.setEmail(ADMIN_EMAIL);
            admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
            userRepository.save(admin);
            log.info("Default admin account created: username={}", AdminAuth.ADMIN_USERNAME);
        }
    }
}
