package com.gymkhata.audit;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.common.clock.Clock;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view over the audit log. A gym owner sees only their own gym's
 * entries; the super admin sees everything.
 */
@Service
public class AuditQueryService {

    private static final String COLLECTION = "audit_logs";

    private final MongoTemplate template;
    private final Clock clock;

    public AuditQueryService(MongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    public List<Document> list(int limit) {
        Query q = new Query();
        if (!SecurityUtils.isSuperAdmin()) {
            q.addCriteria(Criteria.where("gymId").is(SecurityUtils.currentGymId()));
        }
        q.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        q.limit(Math.max(1, Math.min(limit, 500)));
        List<Document> out = new ArrayList<>();
        template.find(q, Document.class, COLLECTION).forEach(out::add);
        return out;
    }
}
