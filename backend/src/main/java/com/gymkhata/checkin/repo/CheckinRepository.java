package com.gymkhata.checkin.repo;

import com.gymkhata.checkin.model.Checkin;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckinRepository extends MongoRepository<Checkin, String> {

    List<Checkin> findByGymIdAndDateOrderByCheckInAtDesc(String gymId, LocalDate date);

    Optional<Checkin> findByGymIdAndMemberIdAndDateAndCheckOutAtIsNull(String gymId, String memberId, LocalDate date);

    List<Checkin> findByGymIdAndMemberIdAndDateOrderByCheckInAtDesc(String gymId, String memberId, LocalDate date);

    List<Checkin> findByGymIdAndMemberIdOrderByCheckInAtDesc(String gymId, String memberId);

    List<Checkin> findByGymIdAndCheckOutAtIsNull(String gymId);

    Checkin findFirstByGymIdAndMemberIdOrderByCheckInAtDesc(String gymId, String memberId);
}
