package com.gymkhata.importer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "CSV Import")
public class ImporterController {

    private final ImporterService importerService;

    public ImporterController(ImporterService importerService) {
        this.importerService = importerService;
    }

    @PostMapping("/members")
    @Operation(summary = "Import members from CSV (name,phone,email,gender,dob,address,state,whatsappOptIn)")
    public ImporterService.ImportResult importMembers(@RequestBody Map<String, String> req) {
        return importerService.importMembers(req.getOrDefault("csv", ""));
    }

    @PostMapping("/payments")
    @Operation(summary = "Import payments from CSV (memberPhone,amount,method,utr)")
    public ImporterService.ImportResult importPayments(@RequestBody Map<String, String> req) {
        return importerService.importPayments(req.getOrDefault("csv", ""));
    }
}
