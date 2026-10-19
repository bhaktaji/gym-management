package com.gymkhata.billing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Atomic gapless sequence per gym per financial year per series.
 * _id = "<gymId>|<fy>|<series>" where series is INV or CN.
 */
@Document("counters")
public class Counter {

    @Id
    private String id;
    private long seq;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }
}
