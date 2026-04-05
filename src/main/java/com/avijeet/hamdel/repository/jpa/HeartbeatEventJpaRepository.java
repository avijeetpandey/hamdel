package com.avijeet.hamdel.repository.jpa;

import com.avijeet.hamdel.entity.HeartbeatEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartbeatEventJpaRepository extends JpaRepository<HeartbeatEventEntity, Long> {

    boolean existsByEventId(String eventId);
}
