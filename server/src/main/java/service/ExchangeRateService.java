package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service fetching the live USD → VND exchange rate from a free public API
 * (open.er-api.com). Results are cached for {@value CACHE_DURATION_MINUTES}
 * minutes to avoid hammering the external endpoint on every PayPal order.
 *
 * <p>Fallback: if the network call fails or the response cannot be parsed,
 * the last successfully fetched rate is returned. If no rate has ever been
 * fetched successfully, the compile-time constant {@value FALLBACK_RATE} is used.
 */
public class ExchangeRateService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    /**
     * Minutes before a cached rate is considered stale and re-fetched.
     */
    private static final long CACHE_DURATION_MINUTES = 10;

    /**
     * Static fallback rate used only when no live data is available at all.
     * Update this if the long-run average drifts significantly.
     */
    private static final double FALLBACK_RATE = 26_500.0;

    /**
     * Free, no-key-required endpoint.
     * Returns JSON: { "rates": { "VND": <double> }, ... }
     */
    private static final String API_URL = "https://open.er-api.com/v6/latest/USD";

    private static final ExchangeRateService INSTANCE = new ExchangeRateService();
    public static ExchangeRateService getInstance() {
        return INSTANCE;
    }

    // Internal state

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /**
     * Guarded by synchronized methods; never null after first successful fetch.
     */
    private final AtomicReference<CachedRate> cachedRate = new AtomicReference<>(null);

    private ExchangeRateService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.mapper = JacksonConfig.mapper();
    }

    // Public API

    /**
     * Returns the current USD → VND exchange rate.
     * Uses a cached value if it is still fresh; otherwise fetches from the API.
     *
     * @return exchange rate (e.g. 25 350.0 means 1 USD = 25 350 VND)
     */
    public double getUsdToVndRate() {
        CachedRate cached = cachedRate.get();
        if (cached != null && !cached.isStale()) {
            return cached.rate;
        }
        return fetchAndCache();
    }

    /**
     * Converts a VND amount to USD using the live exchange rate.
     *
     * @param amountVND amount in Vietnamese Dong (long)
     * @return equivalent amount in USD (double, 2 decimal places precision)
     */
    public double vndToUsd(long amountVND) {
        double rate = getUsdToVndRate();
        return amountVND / rate;
    }

    /**
     * Converts a USD amount back to VND using the live exchange rate.
     *
     * @param amountUSD amount in US Dollars
     * @return equivalent amount in VND (rounded to nearest long)
     */
    public long usdToVnd(double amountUSD) {
        double rate = getUsdToVndRate();
        return Math.round(amountUSD * rate);
    }

    // Internal helpers

    private synchronized double fetchAndCache() {
        // Re-check inside lock in case another thread already refreshed.
        CachedRate existing = cachedRate.get();
        if (existing != null && !existing.isStale()) {
            return existing.rate;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("API returned HTTP {}: {}", response.statusCode(), response.body());
                return fallback();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode vndNode = root.path("rates").path("VND");

            if (vndNode.isMissingNode() || vndNode.isNull()) {
                logger.warn("VND node missing from response: {}", response.body());
                return fallback();
            }

            double rate = vndNode.asDouble();
            if (rate <= 0) {
                logger.warn("Received non-positive rate: {}", rate);
                return fallback();
            }

            logger.info("Live rate fetched: 1 USD = {:,.2f} VND", rate);
            cachedRate.set(new CachedRate(rate));
            return rate;

        } catch (Exception e) {
            logger.error("Failed to fetch live rate: {}", e.getMessage());
            return fallback();
        }
    }

    private double fallback() {
        CachedRate last = cachedRate.get();
        if (last != null) {
            logger.info("Using last known rate: {:,.2f}", last.rate);
            return last.rate;
        }
        logger.warn("No cached rate available; using compile-time fallback: {:,.2f}", FALLBACK_RATE);
        return FALLBACK_RATE;
    }

    // Cache record

    private static class CachedRate {
        final double rate;
        final Instant fetchedAt;

        CachedRate(double rate) {
            this.rate = rate;
            this.fetchedAt = Instant.now();
        }

        boolean isStale() {
            return Instant.now().isAfter(
                    fetchedAt.plusSeconds(CACHE_DURATION_MINUTES * 60));
        }
    }
}