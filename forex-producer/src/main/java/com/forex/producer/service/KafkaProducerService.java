package com.forex.producer.service;

import com.forex.producer.config.KafkaConfig;
import com.forex.producer.model.ForexPrice;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<@NonNull String, @NonNull ForexPrice> kafkaTemplate;

    public void sendForexPrice(ForexPrice forexPrice) {
        try {
            kafkaTemplate.send(KafkaConfig.FOREX_PRICES_RAW_TOPIC,
                    forexPrice.getSymbol(),
                    forexPrice);
            log.info("Sent forex price to Kafka: {}", forexPrice.getSymbol());
        } catch (Exception e) {
            log.error("Error sending forex price to Kafka: {}", e.getMessage());
        }
    }
}