package com.ffl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "app_settings")
@Data
public class AppSetting {

    public static final long SINGLE_ID = 1L;

    @Id
    private Long id;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "target_price", nullable = false)
    private int targetPrice;
}
