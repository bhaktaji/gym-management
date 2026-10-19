package com.gymkhata.common.config;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class PublicConfigController {

    @Value("${app.name}")
    private String appName;

    @Value("${app.pricing.monthly-price-paise}")
    private int monthlyPricePaise;

    @Value("${app.pricing.currency}")
    private String currency;

    @Value("${app.pricing.included}")
    private List<String> included;

    @Value("${app.pricing.not-charged}")
    private List<String> notCharged;

    @GetMapping("/public")
    @Operation(summary = "Public platform config (name, pricing) for landing/pricing pages")
    public Map<String, Object> publicConfig() {
        return Map.of(
                "name", appName,
                "pricing", Map.of(
                        "monthlyPricePaise", monthlyPricePaise,
                        "currency", currency,
                        "included", included,
                        "notCharged", notCharged));
    }
}
