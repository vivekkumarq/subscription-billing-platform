package com.vivek.platform.subscription.config;

import com.vivek.platform.subscription.domain.PlanEntity;
import com.vivek.platform.subscription.domain.PlanType;
import com.vivek.platform.subscription.repository.PlanRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedPlans(PlanRepository planRepository) {
        return args -> {
            if (planRepository.count() == 0) {
                PlanEntity free = new PlanEntity();
                free.setType(PlanType.FREE);
                free.setMonthlyPrice(0.0);
                free.setIncludedUnits(100);

                PlanEntity basic = new PlanEntity();
                basic.setType(PlanType.BASIC);
                basic.setMonthlyPrice(499.0);
                basic.setIncludedUnits(10_000);

                PlanEntity pro = new PlanEntity();
                pro.setType(PlanType.PRO);
                pro.setMonthlyPrice(1999.0);
                pro.setIncludedUnits(100_000);

                planRepository.save(free);
                planRepository.save(basic);
                planRepository.save(pro);
            }
        };
    }
}