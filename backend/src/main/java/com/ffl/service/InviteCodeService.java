package com.ffl.service;

import com.ffl.entity.InviteCode;
import com.ffl.entity.User;
import com.ffl.repository.InviteCodeRepository;
import com.ffl.util.InviteCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteCodeService {

    private static final int MAX_BATCH = 50;

    private final InviteCodeRepository inviteCodeRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<InviteCode> result;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            result = inviteCodeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        } else {
            result = inviteCodeRepository.findByStatusOrderByCreatedAtDesc(
                    InviteCode.Status.valueOf(status.toUpperCase()),
                    PageRequest.of(safePage, safeSize)
            );
        }
        List<Map<String, Object>> content = result.getContent().stream().map(this::toMap).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("page", result.getNumber());
        data.put("totalPages", result.getTotalPages());
        data.put("totalElements", result.getTotalElements());
        return data;
    }

    @Transactional
    public List<String> generate(User admin, int count) {
        int safeCount = Math.min(Math.max(count, 1), MAX_BATCH);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < safeCount; i++) {
            String code = uniqueCode();
            InviteCode invite = new InviteCode();
            invite.setCode(code);
            invite.setStatus(InviteCode.Status.UNUSED);
            invite.setCreatedBy(admin);
            inviteCodeRepository.save(invite);
            codes.add(code);
        }
        log.info("Generated {} invite codes by {}", safeCount, admin.getUsername());
        return codes;
    }

    private String uniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = InviteCodeGenerator.generate();
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate invite code");
    }

    private Map<String, Object> toMap(InviteCode code) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", code.getId());
        map.put("code", code.getCode());
        map.put("status", code.getStatus().name());
        map.put("createdAt", code.getCreatedAt());
        map.put("usedAt", code.getUsedAt());
        map.put("usedBy", code.getUsedBy() == null ? null : code.getUsedBy().getUsername());
        return map;
    }
}
