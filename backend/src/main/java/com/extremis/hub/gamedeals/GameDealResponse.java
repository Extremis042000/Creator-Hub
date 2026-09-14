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
    /** e.g. "Steam", "Epic Games Store" -- which store this specific deal is on. */
    String storeName;
    /**
     * The real purchase link. For Steam deals, a direct link to the
     * official Steam store page. For other stores (e.g. Epic), Steam
     * app IDs don't map to their URLs, so this is CheapShark's own
     * documented redirect endpoint (cheapshark.com/redirect?dealID=...),
     * which forwards straight to that store's real page for this deal --
     * not a third-party ad redirect, CheapShark's own official mechanism.
     */
    String dealUrl;
}
