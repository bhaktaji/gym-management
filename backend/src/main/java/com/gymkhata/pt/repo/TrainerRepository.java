package com.gymkhata.pt.repo;

import com.gymkhata.pt.model.Trainer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TrainerRepository extends MongoRepository<Trainer, String> {

    List<Trainer> findByGymIdOrderByNameAsc(String gymId);

    List<Trainer> findByGymIdAndActiveOrderByNameAsc(String gymId, boolean active);
}
