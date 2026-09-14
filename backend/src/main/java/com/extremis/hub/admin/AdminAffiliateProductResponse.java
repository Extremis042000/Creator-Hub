package com.extremis.hub.admin;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminAffiliateProductResponse {
    UUID id;
    String name;
    String brand;
    String category;
    String priceInfo;
    String affiliateUrl;
    String merchant;
    String region;
    String disclosureText;
    String imageUrl;
    boolean active;
}
