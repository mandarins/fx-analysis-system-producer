package com.forex.producer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForexPrice {
    private String symbol;           // e.g., "EURUSD"
    private String baseCurrency;     // e.g., "EUR"
    private String quoteCurrency;    // e.g., "USD"
    private Double bidPrice;         // Buy price
    private Double askPrice;         // Sell price
    private Double midPrice;         // Average price
    private LocalDateTime timestamp;
    private String source;           // API source name
}
