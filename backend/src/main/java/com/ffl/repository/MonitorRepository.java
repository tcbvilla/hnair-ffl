package com.ffl.repository;

import com.ffl.entity.Monitor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    List<Monitor> findByUserIdOrderByIdDesc(Long userId);

    long countByUserId(Long userId);

    Optional<Monitor> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Monitor m where m.id = :id")
    Optional<Monitor> lockById(@Param("id") Long id);

    List<Monitor> findByEnabledTrueOrderByLastCheckedAtAsc();
}
