package com.schoen.fosreport.dao;

import com.schoen.fosreport.model.EventMetrics;
import com.schoen.fosreport.model.EventWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventMetricsRepository extends JpaRepository<EventMetrics, EventWindow> {
}
