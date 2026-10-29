package com.ffl.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitors")
@Data
public class Monitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(name = "flight_date", nullable = false)
    private LocalDate flightDate;

    @Column(name = "flight_no", length = 8)
    private String flightNo;

    @Column(name = "notify_presence", nullable = false)
    private boolean notifyPresence;

    @Column(name = "notify_quantity", nullable = false)
    private boolean notifyQuantity;

    @Column(name = "inventory_state", length = 2000)
    private String inventoryState;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_success")
    private Boolean lastSuccess;

    @Column(name = "last_hit")
    private Boolean lastHit;

    @Column(name = "last_summary", length = 500)
    private String lastSummary;

    @Column(name = "last_error", length = 300)
    private String lastError;
}
