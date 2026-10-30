package com.gymkhata.pt.repo;

import com.gymkhata.pt.model.PtEnrolment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PtEnrolmentRepository extends MongoRepository<PtEnrolment, String> {

    List<PtEnrolment> findByGymIdOrderByCreatedAtDesc(String gymId);

    List<PtEnrolment> findByGymIdAndMemberIdOrderByCreatedAtDesc(String gymId, String memberId);

    List<PtEnrolment> findByGymIdAndTrainerIdOrderByCreatedAtDesc(String gymId, String trainerId);

    List<PtEnrolment> findByGymIdAndStatus(String gymId, String status);

    Optional<PtEnrolment> findByGymIdAndInvoiceId(String gymId, String invoiceId);
}
