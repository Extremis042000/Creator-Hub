package com.extremis.hub.admin;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminProductResponse {
    UUID id;
    String categorySlug;
    String name;
    long priceCents;
    String currency;
    String fileRef;
    boolean active;
}
