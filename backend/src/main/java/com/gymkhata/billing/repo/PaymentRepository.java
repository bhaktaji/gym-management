package com.gymkhata.billing.repo;

import com.gymkhata.billing.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    List<Payment> findByGymIdAndInvoiceId(String gymId, String invoiceId);

    Optional<Payment> findByGymIdAndUtr(String gymId, String utr);

    List<Payment> findByGymIdAndStatus(String gymId, String status);
}
