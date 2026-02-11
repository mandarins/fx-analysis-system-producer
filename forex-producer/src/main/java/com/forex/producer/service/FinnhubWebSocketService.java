package com.forex.producer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forex.producer.model.ForexPrice;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@ConditionalOnProperty(name = "forex.data.source", havingValue = "LIVE")
public class FinnhubWebSocketService implements SmartLifecycle {

    private static final Map<String, String> SYMBOL_MAP = Map.of(
            "OANDA:EUR_USD", "EURUSD",
            "OANDA:GBP_USD", "GBPUSD",
            "OANDA:USD_JPY", "USDJPY",
            "OANDA:USD_CHF", "USDCHF",
            "OANDA:USD_CAD", "USDCAD"
    );

    private static final double SPREAD_PIPS = 0.0002; // 2 pips for major pairs
    private static final long MAX_RECONNECT_DELAY_MS = 60_000;

    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String websocketUrl;
    private final List<String> symbols;
    private final ScheduledExecutorService reconnectExecutor;

    private WebSocketClient webSocketClient;
    private volatile boolean running = false;
    private long reconnectDelayMs = 1_000;

    public FinnhubWebSocketService(
            KafkaProducerService kafkaProducerService,
            @Value("${finnhub.api-key}") String apiKey,
            @Value("${finnhub.websocket-url}") String websocketUrl,
            @Value("${finnhub.symbols}") List<String> symbols) {
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.websocketUrl = websocketUrl;
        this.symbols = symbols;
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "finnhub-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        log.info("Starting Finnhub WebSocket service (LIVE mode)");
        running = true;
        connect();
    }

    @Override
    public void stop() {
        log.info("Stopping Finnhub WebSocket service");
        running = false;
        reconnectExecutor.shutdownNow();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void connect() {
        try {
            String uri = websocketUrl + "?token=" + apiKey;
            webSocketClient = new WebSocketClient(new URI(uri)) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Finnhub WebSocket connected (status: {})", handshake.getHttpStatus());
                    reconnectDelayMs = 1_000; // Reset backoff on successful connection
                    subscribeToSymbols();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Finnhub WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("Finnhub WebSocket error: {}", ex.getMessage(), ex);
                }
            };

            webSocketClient.connect();
        } catch (Exception e) {
            log.error("Failed to create Finnhub WebSocket connection: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    private void subscribeToSymbols() {
        for (String symbol : symbols) {
            try {
                String subscribeMsg = objectMapper.writeValueAsString(
                        Map.of("type", "subscribe", "symbol", symbol));
                webSocketClient.send(subscribeMsg);
                log.info("Subscribed to {}", symbol);
            } catch (Exception e) {
                log.error("Failed to subscribe to {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.path("type").asText();

            if (!"trade".equals(type)) {
                return;
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) {
                return;
            }

            for (JsonNode trade : dataArray) {
                processTrade(trade);
            }
        } catch (Exception e) {
            log.error("Error processing Finnhub message: {}", e.getMessage(), e);
        }
    }

    private void processTrade(JsonNode trade) {
        String finnhubSymbol = trade.path("s").asText();
        String internalSymbol = SYMBOL_MAP.get(finnhubSymbol);

        if (internalSymbol == null) {
            return;
        }

        double tradePrice = trade.path("p").asDouble();
        long timestampMs = trade.path("t").asLong();

        String baseCurrency = internalSymbol.substring(0, 3);
        String quoteCurrency = internalSymbol.substring(3, 6);

        // Apply synthetic spread: 2 pips around the trade price
        double spreadAmount = isJpyPair(internalSymbol) ? SPREAD_PIPS * 100 : SPREAD_PIPS;
        double bidPrice = tradePrice - (spreadAmount / 2);
        double askPrice = tradePrice + (spreadAmount / 2);

        ForexPrice forexPrice = ForexPrice.builder()
                .symbol(internalSymbol)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .bidPrice(bidPrice)
                .askPrice(askPrice)
                .midPrice(tradePrice)
                .timestamp(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault()))
                .source("FINNHUB")
                .build();

        kafkaProducerService.sendForexPrice(forexPrice);
    }

    private boolean isJpyPair(String symbol) {
        return symbol.contains("JPY");
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }

        log.info("Scheduling reconnect in {} ms", reconnectDelayMs);
        reconnectExecutor.schedule(this::connect, reconnectDelayMs, TimeUnit.MILLISECONDS);

        // Exponential backoff: 1s -> 2s -> 4s -> ... -> 60s max
        reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
    }
}
