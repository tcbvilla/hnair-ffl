package com.ffl.repository;

import com.ffl.entity.MonitorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitorEventRepository extends JpaRepository<MonitorEvent, Long> {

    List<MonitorEvent> findByMonitorIdOrderByIdDesc(Long monitorId);

    void deleteByMonitorId(Long monitorId);
}
