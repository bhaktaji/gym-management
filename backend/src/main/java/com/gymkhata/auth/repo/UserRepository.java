package com.gymkhata.auth.repo;

import com.gymkhata.auth.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByGymIdAndRole(String gymId, String role);

    boolean existsByGymIdAndRole(String gymId, String role);
}
