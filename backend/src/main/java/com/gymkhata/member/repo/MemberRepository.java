package com.gymkhata.member.repo;

import com.gymkhata.member.model.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {

    Optional<Member> findByGymIdAndPhone(String gymId, String phone);

    List<Member> findByGymIdAndStatus(String gymId, String status);

    List<Member> findByGymId(String gymId);

    long countByGymIdAndStatus(String gymId, String status);
}
