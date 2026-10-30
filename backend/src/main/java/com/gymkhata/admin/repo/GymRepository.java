package com.gymkhata.admin.repo;

import com.gymkhata.admin.model.Gym;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GymRepository extends MongoRepository<Gym, String> {

    Optional<Gym> findByCode(String code);

    Optional<Gym> findBySlug(String slug);

    Optional<Gym> findByCodeIgnoreCase(String code);
}
