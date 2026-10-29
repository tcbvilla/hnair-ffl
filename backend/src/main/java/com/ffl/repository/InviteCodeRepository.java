package com.ffl.repository;

import com.ffl.entity.InviteCode;
import com.ffl.entity.InviteCode.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    boolean existsByCode(String code);

    Page<InviteCode> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<InviteCode> findByStatusOrderByCreatedAtDesc(Status status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE invite_codes SET status = :usedStatus, used_by = :userId, used_at = :usedAt "
            + "WHERE code = :code AND status = :unusedStatus", nativeQuery = true)
    int markUsed(@Param("code") String code,
                 @Param("userId") Long userId,
                 @Param("usedAt") LocalDateTime usedAt,
                 @Param("unusedStatus") String unusedStatus,
                 @Param("usedStatus") String usedStatus);
}
