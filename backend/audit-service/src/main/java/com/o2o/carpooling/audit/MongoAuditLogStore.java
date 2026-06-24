package com.o2o.carpooling.audit;

import com.o2o.carpooling.common.domain.AuditLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Repository
class MongoAuditLogStore implements AuditLogStore {

    private final MongoTemplate mongoTemplate;

    MongoAuditLogStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(MongoAuditLogDocument.class)
            .createIndex(new Index().on("targetType", Sort.Direction.ASC).on("targetId", Sort.Direction.ASC).on("occurredAt", Sort.Direction.DESC));
        mongoTemplate.indexOps(MongoAuditLogDocument.class)
            .createIndex(new Index().on("actorId", Sort.Direction.ASC).on("occurredAt", Sort.Direction.DESC));
        mongoTemplate.indexOps(MongoAuditLogDocument.class)
            .createIndex(new Index().on("action", Sort.Direction.ASC).on("occurredAt", Sort.Direction.DESC));
    }

    @Override
    public AuditLog save(AuditLog log) {
        return mongoTemplate.save(MongoAuditLogDocument.from(log)).toDomain();
    }

    @Override
    public AuditLogPage query(AuditQuery query) {
        Query mongoQuery = mongoQuery(query);
        long total = mongoTemplate.count(mongoQuery, MongoAuditLogDocument.class);
        List<AuditLog> items = mongoTemplate.find(
                mongoQuery.skip((long) query.page() * query.size())
                    .limit(query.size())
                    .with(Sort.by(Sort.Direction.DESC, "occurredAt")),
                MongoAuditLogDocument.class
            )
            .stream()
            .map(MongoAuditLogDocument::toDomain)
            .toList();
        return new AuditLogPage(items, query.page(), query.size(), total);
    }

    private Query mongoQuery(AuditQuery query) {
        List<Criteria> criteria = new ArrayList<>();
        query.targetType().filter(StringUtils::hasText).ifPresent(value -> criteria.add(Criteria.where("targetType").is(value)));
        query.targetId().filter(StringUtils::hasText).ifPresent(value -> criteria.add(Criteria.where("targetId").is(value)));
        query.action().filter(StringUtils::hasText).ifPresent(value -> criteria.add(Criteria.where("action").is(value)));
        query.actorId().filter(StringUtils::hasText).ifPresent(value -> criteria.add(Criteria.where("actorId").is(value)));
        Query mongoQuery = new Query();
        if (!criteria.isEmpty()) {
            mongoQuery.addCriteria(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));
        }
        return mongoQuery;
    }
}
