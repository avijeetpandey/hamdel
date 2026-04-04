package com.avijeet.hamdel.adapter.outbound.persistence.repository;

import com.avijeet.hamdel.adapter.outbound.persistence.entity.SessionMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionMetricsJpaRepository extends JpaRepository<SessionMetricsEntity, Long> { }
