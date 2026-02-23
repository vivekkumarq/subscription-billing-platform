package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.InvoiceResponse;
import com.vivek.platform.subscription.service.BillingService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/invoices")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/{orgId}")
    public InvoiceResponse generate(@PathVariable UUID orgId,
                                    @RequestParam int year,
                                    @RequestParam int month) {
        return billingService.generateInvoice(orgId, year, month);
    }
}