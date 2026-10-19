package com.gymkhata.common.config;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.auth.model.User;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.lead.model.Lead;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.plan.model.Plan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.domain.Sort;

import jakarta.annotation.PostConstruct;

@Configuration
public class MongoConfig {

    private final MongoTemplate template;

    public MongoConfig(MongoTemplate template) {
        this.template = template;
    }

    @PostConstruct
    void ensureIndexes() {
        // users: phone globally unique; at most one GYM_OWNER per gym (DB-level enforcement)
        template.indexOps(User.class).ensureIndex(
                new Index().on("phone", Sort.Direction.ASC).unique());
        template.indexOps(User.class).ensureIndex(
                new Index().on("email", Sort.Direction.ASC).unique().sparse());
        template.indexOps(User.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("role").is("GYM_OWNER"))));

        template.indexOps(Gym.class).ensureIndex(
                new Index().on("code", Sort.Direction.ASC).unique());

        // members: phone unique per gym
        template.indexOps(Member.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("phone", Sort.Direction.ASC).unique());

        template.indexOps(Plan.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC));

        template.indexOps(Membership.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("memberId", Sort.Direction.ASC));

        // invoices: number unique per gym
        template.indexOps(Invoice.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("invoiceNumber", Sort.Direction.ASC).unique());

        // payments: UTR unique per gym
        template.indexOps(Payment.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("utr", Sort.Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("utr").exists(true))));

        template.indexOps(Checkin.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("date", Sort.Direction.DESC));

        template.indexOps(Lead.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("phone", Sort.Direction.ASC));

        template.indexOps(OutboxMessage.class).ensureIndex(
                new Index().on("gymId", Sort.Direction.ASC).on("status", Sort.Direction.ASC));
    }
}
