package com.gymkhata.plan.repo;

import com.gymkhata.plan.model.Plan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlanRepository extends MongoRepository<Plan, String> {

    List<Plan> findByGymIdAndActive(String gymId, boolean active);
}
