package com.ffl.service;

import com.ffl.entity.InviteCode;
import com.ffl.entity.User;
import com.ffl.repository.InviteCodeRepository;
import com.ffl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String username, String password, String inviteCode) {
        validate(username, password, inviteCode);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        String code = inviteCode.trim().toUpperCase();
        InviteCode invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("邀请码不存在"));
        if (invite.getStatus() != InviteCode.Status.UNUSED) {
            throw new IllegalArgumentException("邀请码已使用");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(username.toLowerCase() + "@noreply.local");
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);

        int updated = inviteCodeRepository.markUsed(
                code,
                user.getId(),
                LocalDateTime.now(),
                InviteCode.Status.UNUSED.name(),
                InviteCode.Status.USED.name()
        );
        if (updated != 1) {
            throw new IllegalArgumentException("邀请码已使用");
        }
        return user;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return user;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private void validate(String username, String password, String inviteCode) {
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("用户名需为3-20位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (inviteCode == null || !inviteCode.trim().toUpperCase().matches("^[A-Z0-9]{12,32}$")) {
            throw new IllegalArgumentException("邀请码格式不正确");
        }
    }
}
