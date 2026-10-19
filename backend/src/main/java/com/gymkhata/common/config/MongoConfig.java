package com.gymkhata.common.config;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.auth.model.User;
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
    }
}
