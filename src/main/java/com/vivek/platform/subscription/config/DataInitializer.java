package com.vivek.platform.subscription.config;

import com.vivek.platform.subscription.domain.PlanEntity;
import com.vivek.platform.subscription.domain.PlanType;
import com.vivek.platform.subscription.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Seeds the catalogue of plans. Prices are {@link BigDecimal} literals built from strings -
 * {@code new BigDecimal(499.0)} would carry the double's representation error straight into
 * the database.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedPlans(PlanRepository planRepository) {
        return args -> {
            if (planRepository.count() > 0) {
                return;
            }
            planRepository.save(plan(PlanType.FREE, "0.00", 100, "0.00"));
            planRepository.save(plan(PlanType.BASIC, "499.00", 10_000, "0.10"));
            planRepository.save(plan(PlanType.PRO, "1999.00", 100_000, "0.05"));
            log.info("Seeded {} plans", planRepository.count());
        };
    }

    private static PlanEntity plan(PlanType type, String monthlyPrice, int includedUnits, String overageRate) {
        PlanEntity plan = new PlanEntity();
        plan.setType(type);
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice));
        plan.setIncludedUnits(includedUnits);
        plan.setOverageRatePerUnit(new BigDecimal(overageRate));
        plan.setCurrency("USD");
        return plan;
    }
}
