package com.ffl.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitor_events")
@Data
public class MonitorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "initial_record", nullable = false)
    private boolean initialRecord;

    @Column(name = "presence_changed", nullable = false)
    private boolean presenceChanged;

    @Column(name = "quantity_changed", nullable = false)
    private boolean quantityChanged;

    @Column(nullable = false, length = 1000)
    private String detail;
}
