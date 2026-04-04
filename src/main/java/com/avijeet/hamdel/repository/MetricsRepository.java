package com.avijeet.hamdel.repository;

import com.avijeet.hamdel.model.SessionMetrics;

/**
 * Secondary port: store aggregated KPI metrics.
 */
public interface MetricsRepository {

    void save(SessionMetrics metrics);
}
