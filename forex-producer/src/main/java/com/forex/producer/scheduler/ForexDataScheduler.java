package com.forex.producer.scheduler;

import com.forex.producer.model.ForexPrice;
import com.forex.producer.service.ForexApiService;
import com.forex.producer.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ForexDataScheduler {

    private final ForexApiService forexApiService;
    private final KafkaProducerService kafkaProducerService;

    // Run every 5 minutes (300000 milliseconds)
    @Scheduled(fixedRate = 300000)
    public void fetchAndPublishForexData() {
        log.info("Starting scheduled forex data fetch...");

        try {
            // Fetch forex prices from API
            List<ForexPrice> prices = forexApiService.fetchForexPrices();

            // Send each price to Kafka
            for (ForexPrice price : prices) {
                kafkaProducerService.sendForexPrice(price);
            }

            log.info("Successfully published {} forex prices to Kafka", prices.size());

        } catch (Exception e) {
            log.error("Error in scheduled forex data fetch: {}", e.getMessage(), e);
        }
    }

    // Optional: Run immediately on startup for testing
    @Scheduled(initialDelay = 10000, fixedRate = 300000)
    public void initialFetch() {
        log.info("Initial forex data fetch on startup");
        fetchAndPublishForexData();
    }
}