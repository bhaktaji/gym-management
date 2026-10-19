package com.gymkhata;

import com.gymkhata.common.clock.Clock;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

/**
 * Base for integration tests: all integration tests share one throwaway
 * database (gymkhata_test_&lt;random&gt;) that is dropped when the test JVM exits.
 * Documents are cleared between tests; indexes are created by the application on
 * startup and are left intact. No other database is ever touched.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final String TEST_DB = "gymkhata_test_" + UUID.randomUUID().toString().replace("-", "");

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
                client.getDatabase(TEST_DB).drop();
                System.out.println("TEST_DATABASE_DROPPED: " + TEST_DB);
            }
        }));
        System.out.println("TEST_DATABASE: " + TEST_DB);
    }

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected Clock clock;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/" + TEST_DB);
        registry.add("app.super-admin.phone", () -> "+919000000001");
        registry.add("app.super-admin.password", () -> "ChangeMe@123");
    }

    @BeforeEach
    void resetCollections() {
        clearAll();
    }

    protected void clearAll() {
        for (String col : new String[]{"users", "gyms", "members", "plans", "memberships", "invoices",
                "payments", "checkins", "leads", "outbox_messages", "counters", "audit_logs",
                "refresh_tokens", "password_reset_tokens"}) {
            mongoTemplate.getCollection(col).deleteMany(new Document());
        }
    }
}
