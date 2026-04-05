package com.avijeet.hamdel.repository.jpa;

import com.avijeet.hamdel.entity.SessionMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionMetricsJpaRepository extends JpaRepository<SessionMetricsEntity, Long> { }
