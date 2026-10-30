package com.gymkhata.pt.repo;

import com.gymkhata.pt.model.CommissionEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CommissionEntryRepository extends MongoRepository<CommissionEntry, String> {

    List<CommissionEntry> findByGymIdAndTrainerIdAndMonthOrderByCreatedAtAsc(String gymId, String trainerId, String month);

    List<CommissionEntry> findByGymIdAndMonthOrderByCreatedAtAsc(String gymId, String month);

    List<CommissionEntry> findByGymIdAndInvoiceIdOrderByCreatedAtAsc(String gymId, String invoiceId);

    List<CommissionEntry> findByGymIdOrderByCreatedAtDesc(String gymId);
}
