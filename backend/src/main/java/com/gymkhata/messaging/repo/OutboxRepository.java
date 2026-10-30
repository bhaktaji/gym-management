package com.gymkhata.messaging.repo;

import com.gymkhata.messaging.model.OutboxMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface OutboxRepository extends MongoRepository<OutboxMessage, String> {

    List<OutboxMessage> findByGymIdAndDateOrderByCreatedAtAsc(String gymId, LocalDate date);

    List<OutboxMessage> findByGymIdOrderByCreatedAtDesc(String gymId);

    boolean existsByGymIdAndMemberIdAndTypeAndDate(String gymId, String memberId, String type, LocalDate date);
}
