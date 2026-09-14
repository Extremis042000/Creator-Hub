package com.extremis.hub.gamedeals;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Backs the "Hot Game Deals" section with real, live PC game discounts
 * from CheapShark (https://www.cheapshark.com/api/1.0) -- a free,
 * public, no-API-key-required deal aggregator. Covers Steam (storeID 1)
 * and Epic Games Store (storeID 25) -- both real storeIDs confirmed
 * live against CheapShark's own /stores endpoint, not guessed.
 *
 * Quality filter (added after founder feedback that early results
 * included 95%-off shovelware with 0% positive ratings): a deal must
 * carry a real Steam rating of at least MIN_RATING_PERCENT from at
 * least MIN_RATING_COUNT reviews. CheapShark cross-references Steam's
 * rating data for a game even when the deal itself is on a different
 * store, so this works for Epic deals too. A deal with no rating data
 * at all is excluded, not assumed good.
 *
 * Important honesty note (see conversation with founder): this is a
 * content/engagement feature, NOT an affiliate revenue source -- we
 * have no affiliate/referral relationship with Steam, Epic, or
 * CheapShark, so these links earn no commission. The value here is
 * real, dynamic, on-topic content that gives players a reason to keep
 * visiting, which is what actually supports future ad revenue (Phase
 * 21) and SEO traffic -- founder-curated commission-earning links
 * belong in the separate AffiliateProduct system (Phase 18) once the
 * founder is accepted into an actual affiliate program.
 */
@Service
public class GameDealService {

    private static final Logger log = LoggerFactory.getLogger(GameDealService.class);
    private static final String DEALS_URL =
        "https://www.cheapshark.com/api/1.0/deals?storeID=1,25&sortBy=Savings&pageSize=60&onSale=1";
    private static final int MIN_RATING_PERCENT = 70;
    private static final int MIN_RATING_COUNT = 20;
    private static final int MAX_RESULTS = 24;

    private static final Map<String, String> STORE_NAMES = Map.of(
        "1", "Steam",
        "25", "Epic Games Store");

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
            // CheapShark's edge protection rejects requests with no User-Agent
            // (Java's default gets blocked) -- earlier this was worked around
            // with a browser-spoofing UA, but CheapShark's anti-abuse system
            // now explicitly rejects browser-looking UAs from server IPs too
            // (seen live from Render's IPs) and asks for a descriptive,
            // honest one instead -- exactly their own suggested format.
            CheapSharkDeal[] deals = restClient.get()
                .uri(DEALS_URL)
                .header("User-Agent", "ExtremisCreatorHub/1.0 (surya.chowdhury0412@gmail.com)")
                .retrieve()
                .body(CheapSharkDeal[].class);
            if (deals != null) {
                cachedDeals = Arrays.stream(deals)
                    .filter(d -> d.steamAppID() != null && !d.steamAppID().isBlank())
                    .filter(this::passesQualityBar)
                    .map(this::toResponse)
                    .sorted(java.util.Comparator.comparingInt(GameDealResponse::getSavingsPercent).reversed())
                    .limit(MAX_RESULTS)
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

    private boolean passesQualityBar(CheapSharkDeal d) {
        Integer ratingPercent = parseIntOrNull(d.steamRatingPercent());
        Integer ratingCount = parseIntOrNull(d.steamRatingCount());
        return ratingPercent != null && ratingPercent >= MIN_RATING_PERCENT
            && ratingCount != null && ratingCount >= MIN_RATING_COUNT;
    }

    private GameDealResponse toResponse(CheapSharkDeal d) {
        boolean isSteamDeal = "1".equals(d.storeID());
        return GameDealResponse.builder()
            .title(d.title())
            .salePriceUsd(parseDouble(d.salePrice()))
            .normalPriceUsd(parseDouble(d.normalPrice()))
            .savingsPercent((int) Math.round(parseDouble(d.savings())))
            // Steam's own CDN header image (460x215) -- noticeably
            // sharper than CheapShark's small 231x87 "thumb" capsule,
            // and available for every deal here since we only keep
            // deals that have a steamAppID (see the filter above).
            .thumbnailUrl("https://cdn.cloudflare.steamstatic.com/steam/apps/" + d.steamAppID() + "/header.jpg")
            .steamRatingPercent(parseIntOrNull(d.steamRatingPercent()))
            .storeName(STORE_NAMES.getOrDefault(d.storeID(), "Unknown store"))
            .dealUrl(isSteamDeal
                ? "https://store.steampowered.com/app/" + d.steamAppID() + "/"
                : "https://www.cheapshark.com/redirect?dealID=" + d.dealID())
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
