package com.avijeet.hamdel.domain.port.outbound;

import com.avijeet.hamdel.domain.model.SessionMetrics;

/**
 * Secondary port: store aggregated KPI metrics.
 */
public interface MetricsRepository {

    void save(SessionMetrics metrics);
}
