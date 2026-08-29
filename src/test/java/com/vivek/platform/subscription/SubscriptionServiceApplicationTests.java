package com.vivek.platform.subscription;

import com.vivek.platform.subscription.messaging.KafkaTopics;
import com.vivek.platform.subscription.repository.PlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The embedded broker is declared with exactly the same settings as the other integration
 * tests, so they all share one cached application context.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
		kraft = true,
		partitions = 1,
		topics = {KafkaTopics.USAGE_RECORDED, KafkaTopics.USAGE_RECORDED_DLT, KafkaTopics.QUOTA_THRESHOLD})
class SubscriptionServiceApplicationTests {

	@Autowired
	private PlanRepository planRepository;

	@Test
	@DisplayName("the application context starts and the plan catalogue is seeded")
	void contextLoads() {
		assertThat(planRepository.count()).isEqualTo(3);
	}
}
