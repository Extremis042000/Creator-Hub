package com.extremis.hub.gamedeals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw shape of one entry from https://www.cheapshark.com/api/1.0/deals -- a free, no-auth-key, public API. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CheapSharkDeal(
    String title,
    String dealID,
    String salePrice,
    String normalPrice,
    String savings,
    String thumb,
    String steamAppID,
    String steamRatingPercent,
    String steamRatingCount,
    String storeID) {
}
