package com.gymkhata.common.audit;

import com.gymkhata.common.error.GlobalExceptionHandler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Append-only audit log for owner and super-admin actions. Phones are masked.
 */
@Service
public class AuditService {

    private static final String COLLECTION = "audit_logs";

    private final MongoTemplate template;

    public AuditService(MongoTemplate template) {
        this.template = template;
    }

    @Async
    public void log(String gymId, String actorId, String actorRole, String action,
                    String entity, String entityId, String detail) {
        try {
            Document doc = new Document();
            if (gymId != null) {
                doc.put("gymId", gymId);
            }
            doc.put("actorId", actorId);
            doc.put("actorRole", actorRole);
            doc.put("action", action);
            doc.put("entity", entity);
            doc.put("entityId", entityId);
            doc.put("detail", GlobalExceptionHandler.maskPhones(detail));
            doc.put("createdAt", Instant.now());
            template.insert(doc, COLLECTION);
        } catch (Exception e) {
            // audit logging must never break the main flow
        }
    }
}
