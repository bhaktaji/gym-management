package com.gymkhata.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard summary numbers")
    public Map<String, Object> dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/revenue")
    @Operation(summary = "Confirmed revenue by month (last 12 months)")
    public List<Document> revenue() {
        return reportService.revenueByMonth();
    }

    @GetMapping("/expiring")
    @Operation(summary = "Memberships expiring in the next N days (?days=30)")
    public List<Document> expiring(@RequestParam(defaultValue = "30") int days) {
        return reportService.expiringMemberships(days);
    }

    @GetMapping("/checkins")
    @Operation(summary = "Check-ins per day (?days=30)")
    public List<Document> checkins(@RequestParam(defaultValue = "30") int days) {
        return reportService.checkinsPerDay(days);
    }

    @GetMapping("/plans")
    @Operation(summary = "Active members per plan")
    public List<Document> plans() {
        return reportService.membersPerPlan();
    }

    @GetMapping("/pt-revenue")
    @Operation(summary = "Confirmed Personal Training revenue by month")
    public List<Document> ptRevenue() {
        return reportService.ptRevenueByMonth();
    }

    @GetMapping("/trainer-commissions")
    @Operation(summary = "Commission payable per trainer (?month=YYYY-MM)")
    public List<Document> trainerCommissions(@RequestParam(required = false) String month) {
        return reportService.trainerCommissions(month);
    }

    @GetMapping("/trainer-collections")
    @Operation(summary = "Trainer-wise collections")
    public List<Document> trainerCollections() {
        return reportService.trainerCollections();
    }

    @GetMapping("/pt-expiring")
    @Operation(summary = "PT packages expiring soon (?days=7)")
    public List<Document> ptExpiring(@RequestParam(defaultValue = "7") int days) {
        return reportService.expiringPtPackages(days);
    }
}
