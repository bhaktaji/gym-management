package com.gymkhata.lead.repo;

import com.gymkhata.lead.model.Lead;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends MongoRepository<Lead, String> {

    List<Lead> findByGymIdAndStatus(String gymId, String status);

    List<Lead> findByGymId(String gymId);

    Optional<Lead> findByGymIdAndPhone(String gymId, String phone);
}
