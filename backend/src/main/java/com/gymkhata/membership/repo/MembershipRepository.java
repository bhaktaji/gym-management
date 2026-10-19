package com.gymkhata.membership.repo;

import com.gymkhata.membership.model.Membership;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends MongoRepository<Membership, String> {

    List<Membership> findByGymIdAndMemberIdOrderByCreatedAtDesc(String gymId, String memberId);

    Optional<Membership> findByGymIdAndMemberIdAndStatus(String gymId, String memberId, String status);

    Membership findFirstByGymIdAndMemberIdAndStatusOrderByExpiryDateDesc(String gymId, String memberId, String status);

    List<Membership> findByGymIdAndStatus(String gymId, String status);
}
