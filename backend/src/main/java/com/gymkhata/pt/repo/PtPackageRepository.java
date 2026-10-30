package com.gymkhata.pt.repo;

import com.gymkhata.pt.model.PtPackage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PtPackageRepository extends MongoRepository<PtPackage, String> {

    List<PtPackage> findByGymIdOrderByCreatedAtDesc(String gymId);

    List<PtPackage> findByGymIdAndActiveOrderByCreatedAtDesc(String gymId, boolean active);
}
