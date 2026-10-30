package com.gymkhata.common.config;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.auth.model.PasswordResetToken;
import com.gymkhata.auth.model.RefreshToken;
import com.gymkhata.auth.model.User;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.lead.model.Lead;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Configuration
public class MongoConfig {

    private final MongoTemplate template;

    public MongoConfig(MongoTemplate template) {
        this.template = template;
    }

    @PostConstruct
    public void ensureIndexes() {
        // users: phone globally unique; at most one GYM_OWNER per gym (DB-level enforcement)
        safeEnsureIndex(User.class, new Index().on("phone", Sort.Direction.ASC).unique());
        safeEnsureIndex(User.class, new Index().on("email", Sort.Direction.ASC).unique().sparse());
        safeEnsureIndex(User.class, new Index().on("gymId", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("role").is("GYM_OWNER"))));

        safeEnsureIndex(Gym.class, new Index().on("code", Sort.Direction.ASC).unique());
        safeEnsureIndex(Gym.class, new Index().on("slug", Sort.Direction.ASC).unique().sparse());

        // members: phone unique per gym for non-erased members (phone > "")
        safeEnsureIndex(Member.class, new Index().on("gymId", Sort.Direction.ASC).on("phone", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("phone").gt(""))));

        safeEnsureIndex(Plan.class, new Index().on("gymId", Sort.Direction.ASC));

        safeEnsureIndex(Membership.class, new Index().on("gymId", Sort.Direction.ASC).on("memberId", Sort.Direction.ASC));
        safeEnsureIndex(Membership.class, new Index().on("gymId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("expiryDate", Sort.Direction.ASC));

        // Personal Training & Trainer Commissions
        safeEnsureIndex(Trainer.class, new Index().on("gymId", Sort.Direction.ASC));
        safeEnsureIndex(PtPackage.class, new Index().on("gymId", Sort.Direction.ASC));
        safeEnsureIndex(PtEnrolment.class, new Index().on("gymId", Sort.Direction.ASC).on("memberId", Sort.Direction.ASC));
        safeEnsureIndex(PtEnrolment.class, new Index().on("gymId", Sort.Direction.ASC).on("trainerId", Sort.Direction.ASC));
        safeEnsureIndex(CommissionEntry.class, new Index().on("gymId", Sort.Direction.ASC).on("trainerId", Sort.Direction.ASC).on("month", Sort.Direction.ASC));
        safeEnsureIndex(CommissionEntry.class, new Index().on("gymId", Sort.Direction.ASC).on("invoiceId", Sort.Direction.ASC));

        // invoices: number unique per gym; payToken indexed for public pay page
        safeEnsureIndex(Invoice.class, new Index().on("gymId", Sort.Direction.ASC).on("invoiceNumber", Sort.Direction.ASC).unique());
        safeEnsureIndex(Invoice.class, new Index().on("payToken", Sort.Direction.ASC).sparse());

        // payments: UTR unique per gym
        safeEnsureIndex(Payment.class, new Index().on("gymId", Sort.Direction.ASC).on("utr", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("utr").exists(true))));
        safeEnsureIndex(Payment.class, new Index().on("gymId", Sort.Direction.ASC).on("invoiceId", Sort.Direction.ASC));

        safeEnsureIndex(Checkin.class, new Index().on("gymId", Sort.Direction.ASC).on("date", Sort.Direction.DESC));
        safeEnsureIndex(Checkin.class, new Index().on("gymId", Sort.Direction.ASC).on("memberId", Sort.Direction.ASC).on("date", Sort.Direction.DESC));

        safeEnsureIndex(Lead.class, new Index().on("gymId", Sort.Direction.ASC).on("phone", Sort.Direction.ASC));

        safeEnsureIndex(OutboxMessage.class, new Index().on("gymId", Sort.Direction.ASC).on("status", Sort.Direction.ASC));

        // Auth tokens & audit logs
        safeEnsureIndex(RefreshToken.class, new Index().on("tokenHash", Sort.Direction.ASC).unique());
        safeEnsureIndex(RefreshToken.class, new Index().on("familyId", Sort.Direction.ASC));
        safeEnsureIndex(RefreshToken.class, new Index().on("userId", Sort.Direction.ASC));
        safeEnsureIndex(PasswordResetToken.class, new Index().on("tokenHash", Sort.Direction.ASC).unique());
        safeEnsureIndex(PasswordResetToken.class, new Index().on("userId", Sort.Direction.ASC));
        template.indexOps("audit_logs").createIndex(new Index().on("gymId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        template.indexOps("audit_logs").createIndex(new Index().on("createdAt", Sort.Direction.DESC));
    }

    private <T> void safeEnsureIndex(Class<T> entityClass, Index index) {
        try {
            template.indexOps(entityClass).createIndex(index);
        } catch (DataAccessException ex) {
            template.indexOps(entityClass).dropAllIndexes();
            template.indexOps(entityClass).createIndex(index);
        }
    }
}
