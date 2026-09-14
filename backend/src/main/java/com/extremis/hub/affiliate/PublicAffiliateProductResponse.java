package com.extremis.hub.affiliate;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/**
 * affiliateUrl is deliberately never exposed here -- the public site
 * only ever links to /{id}/redirect, so every outbound click is
 * logged server-side rather than trusting the frontend to also fire
 * a separate analytics call.
 */
@Value
@Builder
public class PublicAffiliateProductResponse {
    UUID id;
    String name;
    String brand;
    String category;
    String priceInfo;
    String merchant;
    String disclosureText;
    String imageUrl;
}
