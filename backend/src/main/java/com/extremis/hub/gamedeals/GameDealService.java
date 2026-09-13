package com.extremis.hub.gamedeals;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Backs the "Hot Game Deals" section with real, live PC game discounts
 * from CheapShark (https://www.cheapshark.com/api/1.0) -- a free,
 * public, no-API-key-required deal aggregator. Restricted to
 * storeID=1 (Steam) so every purchase link can point straight at the
 * real, official Steam store page rather than a third-party redirect
 * of uncertain reliability.
 *
 * Important honesty note (see conversation with founder): this is a
 * content/engagement feature, NOT an affiliate revenue source -- we
 * have no affiliate/referral relationship with Steam or CheapShark,
 * so these links earn no commission. The value here is real, dynamic,
 * on-topic content that gives players a reason to keep visiting,
 * which is what actually supports future ad revenue (Phase 21) and
 * SEO traffic -- founder-curated commission-earning links belong in
 * the separate AffiliateProduct system (Phase 18) once the founder is
 * accepted into an actual affiliate program.
 */
@Service
public class GameDealService {

    private static final Logger log = LoggerFactory.getLogger(GameDealService.class);
    private static final String DEALS_URL =
        "https://www.cheapshark.com/api/1.0/deals?storeID=1&sortBy=Savings&pageSize=12&onSale=1";

    // RestClient.Builder isn't an auto-configured bean in this Boot 4.1.1
    // setup (no spring-boot-starter-webmvc RestClientAutoConfiguration
    // match found), so build directly rather than injecting it.
    private final RestClient restClient = RestClient.create();

    private volatile List<GameDealResponse> cachedDeals = List.of();

    @PostConstruct
    void init() {
        refresh();
    }

    @Scheduled(fixedRate = 6, timeUnit = TimeUnit.HOURS)
    void refresh() {
        try {
            // CheapShark's edge protection 403s requests with no browser-like
            // User-Agent (confirmed while testing their /redirect endpoint
            // manually) -- Java's default HTTP client User-Agent gets blocked.
            CheapSharkDeal[] deals = restClient.get()
                .uri(DEALS_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .retrieve()
                .body(CheapSharkDeal[].class);
            if (deals != null) {
                cachedDeals = Arrays.stream(deals)
                    .filter(d -> d.steamAppID() != null && !d.steamAppID().isBlank())
                    .map(this::toResponse)
                    .toList();
            }
        } catch (Exception e) {
            // Keep serving the last good cache -- CheapShark being briefly
            // unreachable shouldn't take down this section of the site.
            log.warn("Failed to refresh game deals from CheapShark", e);
        }
    }

    public List<GameDealResponse> getCurrentDeals() {
        return cachedDeals;
    }

    private GameDealResponse toResponse(CheapSharkDeal d) {
        return GameDealResponse.builder()
            .title(d.title())
            .salePriceUsd(parseDouble(d.salePrice()))
            .normalPriceUsd(parseDouble(d.normalPrice()))
            .savingsPercent((int) Math.round(parseDouble(d.savings())))
            .thumbnailUrl(d.thumb())
            .steamRatingPercent(parseIntOrNull(d.steamRatingPercent()))
            .steamStoreUrl("https://store.steampowered.com/app/" + d.steamAppID() + "/")
            .build();
    }

    private double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Integer parseIntOrNull(String value) {
        try {
            return value != null && !value.isBlank() ? (int) Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
