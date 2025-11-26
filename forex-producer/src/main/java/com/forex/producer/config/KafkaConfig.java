package com.forex.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String FOREX_PRICES_RAW_TOPIC = "forex.prices.raw";

    @Bean
    public NewTopic forexPricesRawTopic() {
        return TopicBuilder.name(FOREX_PRICES_RAW_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}