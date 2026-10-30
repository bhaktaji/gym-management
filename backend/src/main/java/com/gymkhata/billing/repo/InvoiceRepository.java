package com.gymkhata.billing.repo;

import com.gymkhata.billing.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {

    Optional<Invoice> findByGymIdAndInvoiceNumber(String gymId, String invoiceNumber);

    Optional<Invoice> findByPayToken(String payToken);

    List<Invoice> findByGymIdOrderByCreatedAtDesc(String gymId);

    List<Invoice> findByGymIdAndMemberIdOrderByCreatedAtDesc(String gymId, String memberId);

    List<Invoice> findByGymIdAndOriginalInvoiceId(String gymId, String originalInvoiceId);
}
