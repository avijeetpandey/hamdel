package com.avijeet.hamdel.adapter.outbound.persistence.repository;

import com.avijeet.hamdel.adapter.outbound.persistence.entity.HeartbeatEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartbeatEventJpaRepository extends JpaRepository<HeartbeatEventEntity, Long> {

    boolean existsByEventId(String eventId);
}
