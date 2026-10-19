package com.gymkhata.common;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MongoConnectivityTest {

    @Value("${spring.data.mongodb.uri}")
    private String uri;

    @Test
    void connectsToMongoAndReportsTopology() {
        try (MongoClient client = MongoClients.create(uri)) {
            Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            String msg = "setName=" + hello.getString("setName")
                    + ", msg=" + hello.getString("msg")
                    + ", isWritablePrimary=" + hello.getBoolean("isWritablePrimary", false);
            System.out.println("MONGO_TOPOLOGY: " + msg);
            assertTrue(hello.getBoolean("isWritablePrimary", false), "server should be writable primary");
            MongoDatabase db = client.getDatabase("gymkhata_dev");
            assertEquals(1.0, db.runCommand(new Document("ping", 1)).getDouble("ok"));
        }
    }
}
