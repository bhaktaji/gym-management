package com.gymkhata.messaging;

import com.gymkhata.messaging.model.OutboxMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messaging")
@Tag(name = "Messaging")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping("/outbox/generate")
    @Operation(summary = "Generate today's renewal-reminder outbox entries")
    public List<OutboxMessage> generate() {
        return messagingService.generateRenewalReminders();
    }

    @GetMapping("/outbox")
    @Operation(summary = "Today's outbox")
    public List<OutboxMessage> today() {
        return messagingService.todayOutbox();
    }

    @PostMapping("/outbox/{id}/sent")
    @Operation(summary = "Mark an outbox message as sent")
    public OutboxMessage markSent(@PathVariable String id) {
        return messagingService.markSent(id);
    }
}
