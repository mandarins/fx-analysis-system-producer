package com.forex.producer.service;

import com.forex.producer.model.ForexPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class ForexApiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    // Currency pairs to fetch
    private final String[] currencyPairs = {
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF",
            "AUDUSD", "USDCAD", "NZDUSD"
    };

    public List<ForexPrice> fetchForexPrices() {
        List<ForexPrice> prices = new ArrayList<>();

        for (String pair : currencyPairs) {
            try {
                ForexPrice price = generateMockPrice(pair);
                prices.add(price);
                log.info("Fetched price for {}: {}", pair, price.getMidPrice());
            } catch (Exception e) {
                log.error("Error fetching price for {}: {}", pair, e.getMessage());
            }
        }

        return prices;
    }

    // TODO: Replace with actual API call later
    private ForexPrice generateMockPrice(String symbol) {
        String baseCurrency = symbol.substring(0, 3);
        String quoteCurrency = symbol.substring(3, 6);

        // Generate realistic mock prices based on currency pair
        double basePrice = getBasePrice(symbol);
        double spread = basePrice * 0.0002; // 2 pip spread

        double bidPrice = basePrice - (spread / 2);
        double askPrice = basePrice + (spread / 2);
        double midPrice = (bidPrice + askPrice) / 2;

        return ForexPrice.builder()
                .symbol(symbol)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .bidPrice(bidPrice)
                .askPrice(askPrice)
                .midPrice(midPrice)
                .timestamp(LocalDateTime.now())
                .source("MOCK_API")
                .build();
    }

    private double getBasePrice(String symbol) {
        // Realistic base prices for major pairs
        return switch (symbol) {
            case "EURUSD" -> 1.0850 + (random.nextDouble() * 0.01 - 0.005);
            case "GBPUSD" -> 1.2650 + (random.nextDouble() * 0.01 - 0.005);
            case "USDJPY" -> 148.50 + (random.nextDouble() - 0.5);
            case "USDCHF" -> 0.8750 + (random.nextDouble() * 0.01 - 0.005);
            case "AUDUSD" -> 0.6550 + (random.nextDouble() * 0.01 - 0.005);
            case "USDCAD" -> 1.3650 + (random.nextDouble() * 0.01 - 0.005);
            case "NZDUSD" -> 0.5950 + (random.nextDouble() * 0.01 - 0.005);
            default -> 1.0;
        };
    }
}