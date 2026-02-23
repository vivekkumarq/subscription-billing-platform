package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.CreateSubscriptionRequest;
import com.vivek.platform.subscription.domain.SubscriptionEntity;
import com.vivek.platform.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public SubscriptionEntity subscribe(@RequestBody @Valid CreateSubscriptionRequest request) {
        return subscriptionService.createSubscription(
                request.getOrganizationId().toString(),
                request.getPlanType()
        );
    }
}