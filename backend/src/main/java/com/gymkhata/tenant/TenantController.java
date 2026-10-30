package com.gymkhata.tenant;

import com.gymkhata.admin.model.Gym;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Gym settings")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/gym")
    @Operation(summary = "Get the current gym profile + settings")
    public Gym getGym() {
        return tenantService.getGym();
    }

    @GetMapping("/plan-usage")
    @Operation(summary = "Plan & usage: SaaS price, subscription status, usage counters")
    public Map<String, Object> planUsage() {
        return tenantService.planUsage();
    }

    @PutMapping("/settings")
    @Operation(summary = "Update gym settings (UPI, WhatsApp templates, quiet hours, attendance, PT & commission)")
    public Gym updateSettings(@RequestBody Gym.Settings req) {
        return tenantService.updateSettings(req);
    }

    public record ProfileRequest(String name, String phone, String email, String address,
                                String city, String state, String gstin, Boolean gstRegistered,
                                String invoicePrefix) {
    }

    @PutMapping("/profile")
    @Operation(summary = "Update gym profile (name, GST, invoice prefix)")
    public Gym updateProfile(@RequestBody ProfileRequest req) {
        return tenantService.updateProfile(req.name(), req.phone(), req.email(), req.address(),
                req.city(), req.state(), req.gstin(), req.gstRegistered(), req.invoicePrefix());
    }
}
