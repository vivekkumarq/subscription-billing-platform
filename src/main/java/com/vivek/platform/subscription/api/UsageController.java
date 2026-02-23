package com.vivek.platform.subscription.api;

import com.vivek.platform.subscription.api.dto.UsageEventRequest;
import com.vivek.platform.subscription.domain.UsageEventEntity;
import com.vivek.platform.subscription.events.UsageRecordedEvent;
import com.vivek.platform.subscription.messaging.UsageEventProducer;
import com.vivek.platform.subscription.service.UsageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usage-events")
public class UsageController {

    private final UsageEventProducer producer;

    public UsageController(UsageEventProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public void record(@RequestBody @Valid UsageEventRequest request) {
        UsageRecordedEvent event = new UsageRecordedEvent();
        event.setOrganizationId(request.getOrganizationId());
        event.setUnits(request.getUnitsConsumed());
        producer.publish(event);
    }
}