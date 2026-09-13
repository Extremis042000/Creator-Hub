package com.extremis.hub.gamedeals;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GameDealResponse {
    String title;
    double salePriceUsd;
    double normalPriceUsd;
    int savingsPercent;
    String thumbnailUrl;
    Integer steamRatingPercent;
    /** Always the real, official Steam store page -- never a third-party redirect. */
    String steamStoreUrl;
}
