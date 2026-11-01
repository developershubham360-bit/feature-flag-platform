package com.shubham.featureflagplatform.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
     @Query(value="SELECT * FROM outbox_events ORDER BY id LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
     List<OutboxEvent> lockOldest(@Param("batchSize") int batchSize);
}
